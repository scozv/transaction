package models.interop.payload

case class TransactionPayload(amount: Double, tp: String, parentId: Option[String] = None) {

  def asTransaction(id: String) = models.Transaction(id, amount, tp, parentId.getOrElse(""))
}

object TransactionPayload {

  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit val reads: Reads[TransactionPayload] = (
      (__ \ "amount").read[Double] and
      (__ \ "type").read[String] and
      (__ \ "parent_id").readNullable[String]
    ) (TransactionPayload.apply _)
}
