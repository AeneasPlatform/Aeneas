package com.aeneas

import com.aeneas.common.state.ByteStr
import com.aeneas.settings.WalletSettings
import com.aeneas.wallet.Wallet

trait TestWallet {
  protected val testWallet: Wallet = TestWallet.instance
}

object TestWallet {
  private[TestWallet] lazy val instance = Wallet(WalletSettings(None, Some("123"), Some(ByteStr.empty)))
}
