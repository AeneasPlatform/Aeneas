package com.aeneas.transaction

import cats.implicits._
import com.aeneas.common.state.ByteStr
import com.aeneas.lang.ExecutionError
import com.aeneas.lang.directives.DirectiveSet
import com.aeneas.lang.directives.values.{Account, Expression, Asset => AssetType, DApp => DAppType}
import com.aeneas.lang.v1.traits.Environment.{InputEntity, Tthis}
import com.aeneas.state.Blockchain
import com.aeneas.transaction.smart.script.ScriptRunner.TxOrd
import com.aeneas.transaction.smart.{DApp => DAppTarget}
import shapeless._

package object smart {
  def buildThisValue(
      in: TxOrd,
      blockchain: Blockchain,
      ds: DirectiveSet,
      scriptContainerAddress: Tthis
  ): Either[ExecutionError, InputEntity] =
    in.eliminate(
      tx =>
        RealTransactionWrapper(tx, blockchain, ds.stdLibVersion, paymentTarget(ds, scriptContainerAddress))
          .map(Coproduct[InputEntity](_)),
      _.eliminate(
        order => Coproduct[InputEntity](RealTransactionWrapper.ord(order)).asRight[ExecutionError],
        _.eliminate(
          scriptTransfer => Coproduct[InputEntity](scriptTransfer).asRight[ExecutionError],
          _ => ???
        )
      )
    )

  def paymentTarget(
      ds: DirectiveSet,
      scriptContainerAddress: Tthis
  ): AttachedPaymentTarget =
    (ds.scriptType, ds.contentType) match {
      case (Account, DAppType)                 => DAppTarget
      case (Account, Expression)               => InvokerScript
      case (AssetType, Expression) => scriptContainerAddress.eliminate(_ => throw new Exception("Not a AssetId"), _.eliminate(a => AssetScript(ByteStr(a.id)), v => throw new Exception(s"Fail processing tthis value $v")))
      case _                                      => ???
    }
}
