package biz

import models._

object TransactionBiz extends CanConnectDB2[Transaction] {
  override val collectionName: Symbol = 'transactions
}
