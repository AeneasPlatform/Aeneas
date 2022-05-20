package com.aeneas.transaction.smart

import com.aeneas.common.state.ByteStr
import com.aeneas.features.MultiPaymentPolicyProvider._
import com.aeneas.lang.ExecutionError
import com.aeneas.lang.directives.values.StdLibVersion
import com.aeneas.lang.v1.ContractLimits
import com.aeneas.lang.v1.traits.domain.AttachedPayments
import com.aeneas.lang.v1.traits.domain.AttachedPayments._
import com.aeneas.state.Blockchain

object AttachedPaymentExtractor {
  def extractPayments(
    tx:           InvokeScriptTransaction,
    version:      StdLibVersion,
    blockchain:   Blockchain,
    targetScript: AttachedPaymentTarget
  ): Either[ExecutionError, AttachedPayments] =
    if (tx.payments.size <= 1)
      if (version.supportsMultiPayment)
        multiple(tx)
      else
        single(tx)
    else
      if (!blockchain.allowsMultiPayment)
        Left("Multiple payments isn't allowed now")
      else if (!version.supportsMultiPayment)
        Left(scriptErrorMessage(targetScript, version))
      else if (tx.payments.size > ContractLimits.MaxAttachedPaymentAmount)
        Left(s"Script payment amount=${tx.payments.size} should not exceed ${ContractLimits.MaxAttachedPaymentAmount}")
      else
        multiple(tx)

  private def single(tx: InvokeScriptTransaction) =
    Right(AttachedPayments.Single(tx.payments.headOption.map(p => (p.amount, p.assetId.compatId))))

  private def multiple(tx: InvokeScriptTransaction) =
    Right(AttachedPayments.Multi(tx.payments.map(p => (p.amount, p.assetId.compatId))))

  private def scriptErrorMessage(apt: AttachedPaymentTarget, version: StdLibVersion): String = {
    val name = apt match {
      case DApp            => "DApp"
      case InvokerScript   => "Invoker script"
      case AssetScript(id) => s"Attached asset script id=$id"
    }
    s"$name version ${version.id} < ${MultiPaymentSupportedVersion.id} doesn't support multiple payment attachment"
  }
}

trait AttachedPaymentTarget
case object DApp                     extends AttachedPaymentTarget
case object InvokerScript            extends AttachedPaymentTarget
case class  AssetScript(id: ByteStr) extends AttachedPaymentTarget
