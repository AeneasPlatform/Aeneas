package com.aeneas

import java.io.File
import java.nio.file.Files
import java.security.Security
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import cats.instances.all._
import cats.syntax.option._
import com.typesafe.config._
import com.aeneas.account.{Address, AddressScheme}
import com.aeneas.actor.RootActorSystem
import com.aeneas.api.BlockMeta
import com.aeneas.api.common._
import com.aeneas.api.http._
import com.aeneas.api.http.alias.AliasApiRoute
import com.aeneas.api.http.assets.AssetsApiRoute
import com.aeneas.api.http.leasing.LeaseApiRoute
import com.aeneas.common.state.ByteStr
import com.aeneas.consensus.PoSSelector
import com.aeneas.consensus.nxt.api.http.NxtConsensusApiRoute
import com.aeneas.database.{DBExt, Keys, openDB}
import com.aeneas.events.{BlockchainUpdateTriggersImpl, BlockchainUpdated, UtxEvent}
import com.aeneas.extensions.{Context, Extension}
import com.aeneas.features.EstimatorProvider._
import com.aeneas.features.api.ActivationApiRoute
import com.aeneas.history.{History, StorageFactory}
import com.aeneas.http.{DebugApiRoute, NodeApiRoute}
import com.aeneas.lang.ValidationError
import com.aeneas.metrics.Metrics
import com.aeneas.mining.{Miner, MinerDebugInfo, MinerImpl}
import com.aeneas.network.RxExtensionLoader.RxExtensionLoaderShutdownHook
import com.aeneas.network._
import com.aeneas.settings.WavesSettings
import com.aeneas.state.appender.{BlockAppender, ExtensionAppender, MicroblockAppender}
import com.aeneas.state.{Blockchain, BlockchainUpdaterImpl, Diff, Height}
import com.aeneas.transaction.smart.script.trace.TracedResult
import com.aeneas.transaction.{Asset, DiscardedBlocks, Transaction}
import com.aeneas.utils.Schedulers._
import com.aeneas.utils._
import com.aeneas.utx.{UtxPool, UtxPoolImpl}
import com.aeneas.wallet.Wallet
import io.netty.channel.Channel
import io.netty.channel.group.DefaultChannelGroup
import io.netty.util.HashedWheelTimer
import io.netty.util.concurrent.GlobalEventExecutor
import kamon.Kamon
import monix.eval.{Coeval, Task}
import monix.execution.UncaughtExceptionReporter
import monix.execution.schedulers.{ExecutorScheduler, SchedulerService}
import monix.reactive.Observable
import monix.reactive.subjects.ConcurrentSubject
import org.influxdb.dto.Point
import org.iq80.leveldb.DB
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try
import scala.util.control.NonFatal

class Application(val actorSystem: ActorSystem, val settings: WavesSettings, configRoot: ConfigObject, time: NTP) extends ScorexLogging {
  app =>

  import Application._
  import monix.execution.Scheduler.Implicits.{global => scheduler}

  private val db = openDB(settings.dbSettings.directory)

  private val LocalScoreBroadcastDebounce = 1.second

  private val spendableBalanceChanged = ConcurrentSubject.publish[(Address, Asset)]

  private lazy val upnp = new UPnP(settings.networkSettings.uPnPSettings) // don't initialize unless enabled

  private val wallet: Wallet = try {
    Wallet(settings.walletSettings)
  } catch {
    case NonFatal(e) =>
      log.error(s"Failed to open wallet file '${settings.walletSettings.file.get.getAbsolutePath}", e)
      throw e
  }

  private val peerDatabase = new PeerDatabaseImpl(settings.networkSettings)

  // This handler is needed in case Fatal exception is thrown inside the task

  private val stopOnAppendError = UncaughtExceptionReporter { cause =>
    log.error("Error in Appender", cause)
    forceStopApplication(FatalDBError)
  }

  private val appenderScheduler = singleThread("appender", stopOnAppendError)

  private val extensionLoaderScheduler        = singleThread("rx-extension-loader", reporter = log.error("Error in Extension Loader", _))
  private val microblockSynchronizerScheduler = singleThread("microblock-synchronizer", reporter = log.error("Error in Microblock Synchronizer", _))
  private val scoreObserverScheduler          = singleThread("rx-score-observer", reporter = log.error("Error in Score Observer", _))
  private val historyRepliesScheduler         = fixedPool(poolSize = 2, "history-replier", reporter = log.error("Error in History Replier", _))
  private val minerScheduler                  = singleThread("block-miner", reporter = log.error("Error in Miner", _))

  private val blockchainUpdatesScheduler = singleThread("blockchain-updates", reporter = log.error("Error on sending blockchain updates", _))
  private val blockchainUpdated          = ConcurrentSubject.publish[BlockchainUpdated](scheduler)
  private val utxEvents                  = ConcurrentSubject.publish[UtxEvent](scheduler)
  private val blockchainUpdateTriggers   = new BlockchainUpdateTriggersImpl(blockchainUpdated)

  private[this] var miner: Miner with MinerDebugInfo = Miner.Disabled
  private val (blockchainUpdater, levelDB) =
    StorageFactory(settings, db, time, spendableBalanceChanged, blockchainUpdateTriggers, bc => miner.scheduleMining(bc))

  private var rxExtensionLoaderShutdown: Option[RxExtensionLoaderShutdownHook] = None
  private var maybeUtx: Option[UtxPool]                                        = None
  private var maybeNetwork: Option[NS]                                         = None

  private var extensions = Seq.empty[Extension]

  def apiShutdown(): Unit = {
    for {
      u <- maybeUtx
      n <- maybeNetwork
    } yield shutdown(u, n)
  }

  def run(): Unit = {
    // initialization
    implicit val as: ActorSystem = actorSystem

    if (wallet.privateKeyAccounts.isEmpty)
      wallet.generateNewAccounts(1)

    val establishedConnections = new ConcurrentHashMap[Channel, PeerInfo]
    val allChannels            = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
    val utxStorage =
      new UtxPoolImpl(time, blockchainUpdater, spendableBalanceChanged, settings.utxSettings, utxEvents.onNext)
    maybeUtx = Some(utxStorage)

    val timer                 = new HashedWheelTimer()
    val utxSynchronizerLogger = LoggerFacade(LoggerFactory.getLogger(classOf[UtxPoolSynchronizerImpl]))
    val utxSynchronizerScheduler =
      Schedulers.timeBoundedFixedPool(
        timer,
        5.seconds,
        settings.synchronizationSettings.utxSynchronizer.maxThreads,
        "utx-pool-synchronizer",
        reporter = utxSynchronizerLogger.trace("Uncaught exception in UTX Synchronizer", _)
      )
    val utxSynchronizer =
      UtxPoolSynchronizer(
        utxStorage,
        settings.synchronizationSettings.utxSynchronizer,
        allChannels,
        blockchainUpdater.lastBlockInfo,
        utxSynchronizerScheduler
      )

    val knownInvalidBlocks = new InvalidBlockStorageImpl(settings.synchronizationSettings.invalidBlocksStorage)

    val pos = PoSSelector(blockchainUpdater, settings.synchronizationSettings)

    if (settings.minerSettings.enable)
      miner = new MinerImpl(allChannels, blockchainUpdater, settings, time, utxStorage, wallet, pos, minerScheduler, appenderScheduler)

    val processBlock =
      BlockAppender(blockchainUpdater, time, utxStorage, pos, allChannels, peerDatabase, appenderScheduler) _

    val processFork =
      ExtensionAppender(blockchainUpdater, utxStorage, pos, time, knownInvalidBlocks, peerDatabase, appenderScheduler) _
    val processMicroBlock =
      MicroblockAppender(blockchainUpdater, utxStorage, allChannels, peerDatabase, appenderScheduler) _

    import blockchainUpdater.lastBlockInfo

    val lastScore = lastBlockInfo
      .map(_.score)
      .distinctUntilChanged
      .share(scheduler)

    lastScore
      .debounce(LocalScoreBroadcastDebounce)
      .foreach { x =>
        allChannels.broadcast(LocalScoreChanged(x))
      }(scheduler)

    val history = History(blockchainUpdater, blockchainUpdater.liquidBlock, blockchainUpdater.microBlock, db)

    val historyReplier = new HistoryReplier(blockchainUpdater.score, history, settings.synchronizationSettings)(historyRepliesScheduler)

    def rollbackTask(blockId: ByteStr, returnTxsToUtx: Boolean) =
      Task(blockchainUpdater.removeAfter(blockId))
        .executeOn(appenderScheduler)
        .asyncBoundary
        .map {
          case Right(discardedBlocks) =>
            allChannels.broadcast(LocalScoreChanged(blockchainUpdater.score))
            if (returnTxsToUtx) utxStorage.addAndCleanup(discardedBlocks.view.flatMap(_._1.transactionData))
            Right(discardedBlocks)
          case Left(error) => Left(error)
        }

    // Extensions start
    val extensionContext: Context = new Context {
      override def settings: WavesSettings                                                       = app.settings
      override def blockchain: Blockchain                                                        = app.blockchainUpdater
      override def rollbackTo(blockId: ByteStr): Task[Either[ValidationError, DiscardedBlocks]]  = rollbackTask(blockId, returnTxsToUtx = false)
      override def time: Time                                                                    = app.time
      override def wallet: Wallet                                                                = app.wallet
      override def utx: UtxPool                                                                  = utxStorage
      override def broadcastTransaction(tx: Transaction): TracedResult[ValidationError, Boolean] = utxSynchronizer.publish(tx)
      override def spendableBalanceChanged: Observable[(Address, Asset)]                         = app.spendableBalanceChanged
      override def actorSystem: ActorSystem                                                      = app.actorSystem
      override def utxEvents: Observable[UtxEvent]                                               = app.utxEvents
      override def blockchainUpdated: Observable[BlockchainUpdated]                              = app.blockchainUpdated

      override val transactionsApi: CommonTransactionsApi = CommonTransactionsApi(
        blockchainUpdater.bestLiquidDiff.map(diff => Height(blockchainUpdater.height) -> diff),
        db,
        blockchainUpdater,
        utxStorage,
        wallet,
        utxSynchronizer.publish,
        loadBlockAt(db, blockchainUpdater)
      )
      override val blocksApi: CommonBlocksApi     = CommonBlocksApi(blockchainUpdater, loadBlockMetaAt(db, blockchainUpdater), loadBlockInfoAt(db, blockchainUpdater))
      override val accountsApi: CommonAccountsApi = CommonAccountsApi(blockchainUpdater.bestLiquidDiff.getOrElse(Diff.empty), db, blockchainUpdater)
      override val assetsApi: CommonAssetsApi     = CommonAssetsApi(blockchainUpdater.bestLiquidDiff.getOrElse(Diff.empty), db, blockchainUpdater)
    }

    extensions = settings.extensions.map { extensionClassName =>
      val extensionClass = Class.forName(extensionClassName).asInstanceOf[Class[Extension]]
      val ctor           = extensionClass.getConstructor(classOf[Context])
      log.info(s"Enable extension: $extensionClassName")
      ctor.newInstance(extensionContext)
    }
    extensions.foreach(_.start())

    // Node start
    // After this point, node actually starts doing something
    checkGenesis(settings, blockchainUpdater)

    val network =
      NetworkServer(settings, lastBlockInfo, historyReplier, utxStorage, peerDatabase, allChannels, establishedConnections)
    maybeNetwork = Some(network)
    val (signatures, blocks, blockchainScores, microblockInvs, microblockResponses, transactions) = network.messages

    val timeoutSubject: ConcurrentSubject[Channel, Channel] = ConcurrentSubject.publish[Channel]

    val (syncWithChannelClosed, scoreStatsReporter) = RxScoreObserver(
      settings.synchronizationSettings.scoreTTL,
      1.second,
      blockchainUpdater.score,
      lastScore,
      blockchainScores,
      network.closedChannels,
      timeoutSubject,
      scoreObserverScheduler
    )
    val (microblockData, mbSyncCacheSizes) = MicroBlockSynchronizer(
      settings.synchronizationSettings.microBlockSynchronizer,
      peerDatabase,
      lastBlockInfo.map(_.id),
      microblockInvs,
      microblockResponses,
      microblockSynchronizerScheduler
    )
    val (newBlocks, extLoaderState, sh) = RxExtensionLoader(
      settings.synchronizationSettings.synchronizationTimeout,
      Coeval(blockchainUpdater.lastBlockIds(settings.synchronizationSettings.maxRollback)),
      peerDatabase,
      knownInvalidBlocks,
      blocks,
      signatures,
      syncWithChannelClosed,
      extensionLoaderScheduler,
      timeoutSubject
    ) {
      case (c, b) =>
        processFork(c, b.blocks).doOnFinish {
          case None    => Task.now(())
          case Some(e) => Task(stopOnAppendError.reportFailure(e))
        }
    }

    rxExtensionLoaderShutdown = Some(sh)

    transactions.foreach {
      case (channel, transaction) => utxSynchronizer.tryPublish(transaction, channel)
    }

    val microBlockSink = microblockData
      .mapEval(processMicroBlock.tupled)

    val blockSink = newBlocks
      .mapEval(processBlock.tupled)

    Observable(microBlockSink, blockSink).merge
      .onErrorHandle(stopOnAppendError.reportFailure)
      .subscribe()

    miner.scheduleMining()

    for (addr <- settings.networkSettings.declaredAddress if settings.networkSettings.uPnPSettings.enable) {
      upnp.addPort(addr.getPort)
    }

    // API start
    if (settings.restAPISettings.enable) {
      if (settings.restAPISettings.apiKeyHash == "H6nsiifwYKYEx6YzYD7woP1XCn72RVvx6tC1zjjLXqsu") {
        log.error(
          "Usage of the default api key hash (H6nsiifwYKYEx6YzYD7woP1XCn72RVvx6tC1zjjLXqsu) is prohibited, please change it in the waves.conf"
        )
        forceStopApplication(InvalidApiKey)
      }

      def loadBalanceHistory(address: Address): Seq[(Int, Long)] = db.readOnly { rdb =>
        rdb.get(Keys.addressId(address)).fold(Seq.empty[(Int, Long)]) { aid =>
          rdb.get(Keys.wavesBalanceHistory(aid)).map { h =>
            h -> rdb.get(Keys.wavesBalance(aid)(h))
          }
        }
      }

      val limitedScheduler =
        Schedulers.timeBoundedFixedPool(
          new HashedWheelTimer(),
          5.seconds,
          settings.restAPISettings.limitedPoolThreads,
          "rest-time-limited",
          reporter = log.trace("Uncaught exception in time limited pool", _)
        )

      val apiRoutes = Seq(
        NodeApiRoute(settings.restAPISettings, blockchainUpdater, () => apiShutdown()),
        BlocksApiRoute(settings.restAPISettings, extensionContext.blocksApi),
        TransactionsApiRoute(
          settings.restAPISettings,
          extensionContext.transactionsApi,
          wallet,
          blockchainUpdater,
          () => utxStorage.size,
          utxSynchronizer,
          time
        ),
        NxtConsensusApiRoute(settings.restAPISettings, blockchainUpdater),
        WalletApiRoute(settings.restAPISettings, wallet),
        UtilsApiRoute(time, settings.restAPISettings, blockchainUpdater.estimator, limitedScheduler, blockchainUpdater),
        PeersApiRoute(settings.restAPISettings, network.connect, peerDatabase, establishedConnections),
        AddressApiRoute(settings.restAPISettings, wallet, blockchainUpdater, utxSynchronizer, time, limitedScheduler, extensionContext.accountsApi),
        DebugApiRoute(
          settings,
          time,
          blockchainUpdater,
          wallet,
          extensionContext.accountsApi,
          extensionContext.transactionsApi,
          extensionContext.assetsApi,
          peerDatabase,
          establishedConnections,
          (id, returnTxs) => rollbackTask(id, returnTxs).map(_.map(_ => ())),
          utxStorage,
          miner,
          historyReplier,
          extLoaderState,
          mbSyncCacheSizes,
          scoreStatsReporter,
          configRoot,
          loadBalanceHistory
        ),
        AssetsApiRoute(
          settings.restAPISettings,
          wallet,
          utxSynchronizer,
          blockchainUpdater,
          time,
          extensionContext.accountsApi,
          extensionContext.assetsApi
        ),
        ActivationApiRoute(settings.restAPISettings, settings.featuresSettings, blockchainUpdater),
        LeaseApiRoute(settings.restAPISettings, wallet, blockchainUpdater, utxSynchronizer, time, extensionContext.accountsApi),
        AliasApiRoute(settings.restAPISettings, extensionContext.transactionsApi, wallet, utxSynchronizer, time, blockchainUpdater),
        RewardApiRoute(blockchainUpdater, extensionContext.assetsApi)
      )

      val combinedRoute = CompositeHttpService(apiRoutes, settings.restAPISettings)(actorSystem).loggingCompositeRoute
      val httpFuture    = Http().bindAndHandle(combinedRoute, settings.restAPISettings.bindAddress, settings.restAPISettings.port)
      serverBinding = Await.result(httpFuture, 20.seconds)
      log.info(s"REST API was bound on ${settings.restAPISettings.bindAddress}:${settings.restAPISettings.port}")
    }

    // on unexpected shutdown
    sys.addShutdownHook {
      timer.stop()
      shutdown(utxStorage, network)
    }
  }

  private val shutdownInProgress             = new AtomicBoolean(false)
  @volatile var serverBinding: ServerBinding = _

  def shutdown(utx: UtxPool, network: NS): Unit =
    if (shutdownInProgress.compareAndSet(false, true)) {

      if (extensions.nonEmpty) {
        log.info(s"Shutting down extensions")
        Await.ready(Future.sequence(extensions.map(_.shutdown())), settings.extensionsShutdownTimeout)
      }

      spendableBalanceChanged.onComplete()
      utx.close()

      shutdownAndWait(historyRepliesScheduler, "HistoryReplier", 5.minutes.some)

      log.info("Closing REST API")
      if (settings.restAPISettings.enable)
        Try(Await.ready(serverBinding.unbind(), 2.minutes)).failed.map(e => log.error("Failed to unbind REST API port", e))
      for (addr <- settings.networkSettings.declaredAddress if settings.networkSettings.uPnPSettings.enable) upnp.deletePort(addr.getPort)

      log.debug("Closing peer database")
      peerDatabase.close()

      Try(Await.result(actorSystem.terminate(), 2.minute)).failed.map(e => log.error("Failed to terminate actor system", e))
      log.debug("Node's actor system shutdown successful")

      blockchainUpdater.shutdown()
      rxExtensionLoaderShutdown.foreach(_.shutdown())

      log.info("Stopping network services")
      network.shutdown()

      blockchainUpdated.onComplete()

      shutdownAndWait(blockchainUpdatesScheduler, "BlockchainUpdated")
      shutdownAndWait(minerScheduler, "Miner")
      shutdownAndWait(microblockSynchronizerScheduler, "MicroblockSynchronizer")
      shutdownAndWait(scoreObserverScheduler, "ScoreObserver")
      shutdownAndWait(extensionLoaderScheduler, "ExtensionLoader")

      appenderScheduler.execute(() => levelDB.close())

      shutdownAndWait(appenderScheduler, "Appender", 5.minutes.some, tryForce = false)

      log.info("Closing storage")
      db.close()

      time.close()
      log.info("Shutdown complete")
    }

  private def shutdownAndWait(scheduler: SchedulerService, name: String, timeout: Option[FiniteDuration] = none, tryForce: Boolean = true): Unit = {
    log.debug(s"Shutting down $name")
    scheduler match {
      case es: ExecutorScheduler if tryForce => es.executor.shutdownNow()
      case s                                 => s.shutdown()
    }
    timeout.foreach { to =>
      val r = Await.result(scheduler.awaitTermination(to, global), 2 * to)
      if (r)
        log.info(s"$name was shutdown successfully")
      else
        log.warn(s"Failed to shutdown $name properly during timeout")
    }
  }
}

object Application extends ScorexLogging {
  private[aeneas] def loadApplicationConfig(external: Option[File] = None): WavesSettings = {
    import com.aeneas.settings._

    val config = loadConfig(external.map(ConfigFactory.parseFile))

    // DO NOT LOG BEFORE THIS LINE, THIS PROPERTY IS USED IN logback.xml
    System.setProperty("node.directory", config.getString("node.directory"))
    if (config.hasPath("node.config.directory")) System.setProperty("node.config.directory", config.getString("node.config.directory"))

    external match {
      case None    => log.debug("Config file not defined, TESTNET config will be used")
      case Some(f) => if (!Files.exists(f.toPath)) log.warn(s"Config ${f.toPath.toAbsolutePath.toString} not found, TESTNET config will be used")
    }

    val settings = WavesSettings.fromRootConfig(config)

    // Initialize global var with actual address scheme
    AddressScheme.current = new AddressScheme {
      override val chainId: Byte = settings.blockchainSettings.addressSchemeCharacter.toByte
    }

    // IMPORTANT: to make use of default settings for histograms and timers, it's crucial to reconfigure Kamon with
    //            our merged config BEFORE initializing any metrics, including in settings-related companion objects
    Kamon.reconfigure(config)
    sys.addShutdownHook {
      Try(Await.result(Kamon.stopModules(), 30 seconds))
      Metrics.shutdown()
    }

    if (config.getBoolean("kamon.enable")) {
      Kamon.loadModules()
    }

    settings
  }

  private[aeneas] def loadBlockAt(db: DB, blockchainUpdater: BlockchainUpdaterImpl)(height: Int): Option[(BlockMeta, Seq[Transaction])] =
    loadBlockInfoAt(db, blockchainUpdater)(height).map { case (meta, txs) => (meta, txs.map(_._1)) }

  private[aeneas] def loadBlockInfoAt(db: DB, blockchainUpdater: BlockchainUpdaterImpl)(height: Int): Option[(BlockMeta, Seq[(Transaction, Boolean)])] =
    loadBlockMetaAt(db, blockchainUpdater)(height).map { meta =>
      meta -> blockchainUpdater
        .liquidTransactions(meta.id)
        .orElse(db.readOnly(ro => database.loadTransactions(Height(height), ro)))
        .fold(Seq.empty[(Transaction, Boolean)])(identity)
    }

  private[aeneas] def loadBlockMetaAt(db: DB, blockchainUpdater: BlockchainUpdaterImpl)(height: Int): Option[BlockMeta] =
    blockchainUpdater.liquidBlockMeta.filter(_ => blockchainUpdater.height == height).orElse(db.get(Keys.blockMetaAt(Height(height))))

  def main(args: Array[String]): Unit = {

    // prevents java from caching successful name resolutions, which is needed e.g. for proper NTP server rotation
    // http://stackoverflow.com/a/17219327
    System.setProperty("sun.net.inetaddr.ttl", "0")
    System.setProperty("sun.net.inetaddr.negative.ttl", "0")
    Security.setProperty("networkaddress.cache.ttl", "0")
    Security.setProperty("networkaddress.cache.negative.ttl", "0")

    // specify aspectj to use it's build-in infrastructure
    // http://www.eclipse.org/aspectj/doc/released/pdguide/trace.html
    System.setProperty("org.aspectj.tracing.factory", "default")

    args.headOption.getOrElse("") match {
      case "export"                 => Exporter.main(args.tail)
      case "import"                 => Importer.main(args.tail)
      case "explore"                => Explorer.main(args.tail)
      case "util"                   => UtilApp.main(args.tail)
      case "help" | "--help" | "-h" => println("Usage: waves <config> | export | import | explore | util")
      case _                        => startNode(args.headOption)
    }
  }

  private[this] def startNode(configFile: Option[String]): Unit = {
    import com.aeneas.settings.Constants
    val settings = loadApplicationConfig(configFile.map(new File(_)))

    println (configFile.getOrElse("fnf"))
    val log = LoggerFacade(LoggerFactory.getLogger(getClass))
    log.info("Starting...")
    sys.addShutdownHook {
      SystemInformationReporter.report(settings.config)
    }

    val time = new NTP(settings.ntpServer)
    Metrics.start(settings.metrics, time)

    def dumpMinerConfig(): Unit = {
      import settings.synchronizationSettings.microBlockSynchronizer
      import settings.{minerSettings => miner}

      Metrics.write(
        Point
          .measurement("config")
          .addField("miner-micro-block-interval", miner.microBlockInterval.toMillis)
          .addField("miner-max-transactions-in-micro-block", miner.maxTransactionsInMicroBlock)
          .addField("miner-min-micro-block-age", miner.minMicroBlockAge.toMillis)
          .addField("mbs-wait-response-timeout", microBlockSynchronizer.waitResponseTimeout.toMillis)
      )
    }

    RootActorSystem.start("aeneas", settings.config) { actorSystem =>
      dumpMinerConfig()
      log.info(s"${Constants.AgentName} Blockchain Id: ${settings.blockchainSettings.addressSchemeCharacter}")
      new Application(actorSystem, settings, settings.config.root(), time).run()
    }
  }
}
