package com.aeneas.transaction.validation.impl

import cats.data.ValidatedNel
import com.aeneas.lang.ValidationError
import com.aeneas.transaction.TxValidationError.{GenericError, OrderValidationError}
import com.aeneas.transaction.TxVersion
import com.aeneas.transaction.assets.exchange.{ExchangeTransaction, Order, OrderType}
import com.aeneas.transaction.validation.TxValidator

object ExchangeTxValidator extends TxValidator[ExchangeTransaction] {
  override def validate(tx: ExchangeTransaction): ValidatedNel[ValidationError, ExchangeTransaction] = {
    import tx._

    V.seq(tx)(
      V.fee(fee),
      V.positiveAmount(amount, "assets"),
      V.cond(amount <= Order.MaxAmount, GenericError("amount too large")),
      V.cond(price > 0, GenericError("price should be > 0")),
      V.cond(price <= Order.MaxAmount, GenericError("price too large")),
      V.cond(sellMatcherFee <= Order.MaxAmount, GenericError("sellMatcherFee too large")),
      V.cond(buyMatcherFee <= Order.MaxAmount, GenericError("buyMatcherFee too large")),
      V.cond(fee <= Order.MaxAmount, GenericError("fee too large")),
      V.cond(isProtobufVersion || order1.orderType == OrderType.BUY, GenericError("order1 should have OrderType.BUY")),
      V.cond(buyOrder.orderType == OrderType.BUY, GenericError("buyOrder should has OrderType.BUY")),
      V.cond(sellOrder.orderType == OrderType.SELL, GenericError("sellOrder should has OrderType.SELL")),
      V.cond(buyOrder.matcherPublicKey == sellOrder.matcherPublicKey, GenericError("buyOrder.matcher should be the same as sellOrder.matcher")),
      V.cond(buyOrder.assetPair == sellOrder.assetPair, GenericError("Both orders should have same AssetPair")),
      V.cond(buyOrder.isValid(timestamp), OrderValidationError(buyOrder, buyOrder.isValid(timestamp).messages())),
      V.cond(sellOrder.isValid(timestamp), OrderValidationError(sellOrder, sellOrder.isValid(timestamp).messages())),
      V.cond(
        version > TxVersion.V1 || (buyOrder.version == Order.V1 && sellOrder.version == Order.V1),
        GenericError("can only contain orders of version 1")
      ),
      V.cond(
        version > TxVersion.V2 || (buyOrder.version < Order.V4 && sellOrder.version < Order.V4),
        GenericError("can only contain orders of version < 4")
      )
    )
  }
}
