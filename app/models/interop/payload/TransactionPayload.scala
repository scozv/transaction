
package models.interop.payload

import models.interop.CanBeJsonfied

case class TransactionPayload(amount: Double, tp: String, parentId: Option[String] = None) {

  def asTransaction(id: String) = models.Transaction(id, amount, tp, parentId.getOrElse(""))
}

object TransactionPayload extends CanBeJsonfied[TransactionPayload] {

  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit val writes = new OWrites[TransactionPayload] {
    def writes(tx: TransactionPayload) = Json.obj(
      "amount" -> tx.amount,
      "type" -> tx.tp
    ) ++ tx.parentId.map(x => Json.obj("parent_id" -> x)).getOrElse(Json.obj())
  }

  implicit val reads: Reads[TransactionPayload] = (
      (__ \ "amount").read[Double] and
      (__ \ "type").read[String] and
      (__ \ "parent_id").readNullable[String]
    ) (TransactionPayload.apply _)
}
