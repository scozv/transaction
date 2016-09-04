
package models.interop.payload

import models.interop.CanBeJsonfied

case class TransactionPayload(amount: Double, tp: String, parentId: Option[Long] = None) {

  def asTransaction(id: Long) = models.Transaction(id, amount, tp, parentId)
}

object TransactionPayload extends CanBeJsonfied[TransactionPayload] {

  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  implicit val writes = new OWrites[TransactionPayload] {
    def writes(tx: TransactionPayload) = Json.obj(
      "amount" -> tx.amount,
      "type" -> tx.tp
    ) ++ tx.parentId.map(x => Json.obj("parent_id" -> x)).getOrElse(Json.obj())
  }

  implicit val reads: Reads[TransactionPayload] = (
      (__ \ "amount").read[Double] and
      (__ \ "type").read[String] and
      (__ \ "parent_id").readNullable[Long]
    ) (TransactionPayload.apply _)
}
