package models.interop

import play.api.libs.json._
import play.api.mvc.RequestHeader

class HTTPResponse
(val data: JsValue,
 val ok: Boolean,
 val error: HTTPResponseError)

object HTTPResponse extends CanBeJsonfied[HTTPResponse] {
  import play.api.libs.functional.syntax._
  import play.api.libs.json._

  def apply(data: JsValue, ok: Boolean, error: HTTPResponseError): HTTPResponse =
    new HTTPResponse(data, ok, error)

  def apply(data: JsValue): HTTPResponse = new HTTPResponse(data, true, HTTPResponseError.OK)
  def apply(error: HTTPResponseError): HTTPResponse = new HTTPResponse(Json.obj(), false, error)
  def apply(data: String): HTTPResponse = HTTPResponse(JsString(data))

  implicit val writes = new OWrites[HTTPResponse] {
    def writes(x: HTTPResponse) =
      if (x.ok) Json.obj("ok" -> x.ok, "data" -> x.data, "error" -> "")
      else Json.obj("ok" -> x.ok, "error" -> x.error._id, "data" -> x.error.message)
  }

  implicit val reads: Reads[HTTPResponse] = (
    (__ \ "data").read[JsValue] and
      (__ \ "ok").read[Boolean] and
      (__ \ "error").read[String].map(HTTPResponseError.buildForm)
    )((x, y, z) => HTTPResponse.apply(x, y, z))
}

trait HTTPResponseError {
  val _id: String
  val message: String

  override def toString = s"${_id}: $message"
  def test(errorCode: String): Boolean = _id == errorCode
  def test(error: HTTPResponseError): Boolean = test(error._id)
}

object HTTPResponseError {
  def buildForm(errorCode: String): HTTPResponseError =
    allErrors.getOrElse(errorCode, UNDEFINED)

  // 001~699 see below
  case class e(_id: String, message: String)
    extends HTTPResponseError
  // 7?? : 3rd party interop error
  val UNDEFINED = e("000", "error undefined")
  val OK = e("999", "everything's gonna be alright")
  // 0?? : data validation
  // 2?? : data manipulated failure
  val MONGO_ID_DUPLICATED = e("205", "data _id duplicated")
  val MONGO_SET_FAILED = e("203", "failed to set data in MongoDB")
  case class MONGO_NOT_FOUND(requestPath: String = "") extends HTTPResponseError {
    val _id = "201"
    val message = s"not found record in MongoDB from ${requestPath}"
  }
  object MONGO_NOT_FOUND {
    def apply(request: RequestHeader): MONGO_NOT_FOUND = MONGO_NOT_FOUND(s"${request.method} ${request.path}")
  }

  private val allErrors = List(
    OK, UNDEFINED,
    MONGO_SET_FAILED, MONGO_NOT_FOUND(), MONGO_ID_DUPLICATED
  ).map(x=> x._id -> x).toMap
}