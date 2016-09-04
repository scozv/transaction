package models

import models.interop.{CanBeHierarchicInstance, CanBeHierarchicObject, CanBeJsonfied}

case class Transaction(_id: Long, amount: Double, tp: String, rootId: Option[Long] = None) {

  val isRoot = rootId.isEmpty
  def withId(id: Long) = Transaction(id, amount, tp, rootId)
  def asPayload = interop.payload.TransactionPayload(amount, tp, rootId)
}

object Transaction extends CanBeJsonfied[Transaction] with CanBeHierarchicObject {
  override val rootFieldName = "parent_id"

  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit val writes = new OWrites[Transaction] {
    def writes(tx: Transaction) = Json.obj(
      "_id" -> tx._id,
      "transaction_id" -> tx._id,
      "amount" -> tx.amount,
      "type" -> tx.tp
    ) ++ {
      if (tx.rootId.isEmpty) Json.obj()
      else Json.obj("parent_id" -> tx.rootId)
    }
  }

  implicit val reads: Reads[Transaction] = (
      (__ \ "_id").read[Long] and
      (__ \ "amount").read[Double] and
      (__ \ "type").read[String] and
      (__ \ rootFieldName).readNullable[Long]
    ) (Transaction.apply _)
}




