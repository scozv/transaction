package biz

import models._

object TransactionBiz extends CanConnectDB2[Transaction] {
  override val collectionName: Symbol = 'transactions
}

import play.api.libs.json._
import play.modules.reactivemongo.json.collection.JSONCollection

object QueryBuilder {
  val defaultIdentityField = "_id"

  val universal = Json.obj()
  def withId(id: Long, identityField: String = defaultIdentityField): JsObject =
    Json.obj(identityField -> JsNumber(id))
  def fieldsProjection(fields: String*): JsObject = JsObject(fields.map (_ -> JsBoolean(true)))
  def or(selector: JsObject*): JsObject = Json.obj("$or" -> Json.toJson(selector))
}

trait CanConnectDB2[T] {
  import play.modules.reactivemongo.json.JSONSerializationPack
  import reactivemongo.api._
  import reactivemongo.api.collections.GenericQueryBuilder
  import reactivemongo.api.commands._

  import scala.concurrent.{ExecutionContext, Future}

  val pack = JSONSerializationPack
  type Self <: GenericQueryBuilder[pack.type]

  val collectionName: Symbol

  protected def ctx(db: DB) = db.collection[JSONCollection](collectionName.name)

  def list(db: DB)(implicit swriter: pack.Writer[JsObject], reader: pack.Reader[T], ec: ExecutionContext): Future[Seq[T]] =
    ctx(db).find(QueryBuilder.universal).cursor[T]().collect[Seq]()

  def one(db: DB, id: Long)(implicit swriter: pack.Writer[JsObject], reader: pack.Reader[T], ec: ExecutionContext): Future[Option[T]] =
    one(db, QueryBuilder.withId(id))

  private def one(db: DB, selector: JsObject)(implicit swriter: pack.Writer[JsObject], reader: pack.Reader[T], ec: ExecutionContext): Future[Option[T]] =
    ctx(db).find(selector).one[T]

  def field[B]
  (db: DB, id: Long, fieldName: String)
  (implicit swriter: pack.Writer[JsObject], reader: pack.Reader[T], ec: ExecutionContext, rds: Reads[B]): Future[Option[B]] =
    ctx(db).find(QueryBuilder.withId(id), QueryBuilder.fieldsProjection(fieldName)).one[JsObject]
      .map { x => x.map ( _ \ fieldName).map (_.as[B]) }

  def sequence[B]
  (db: DB, selector: JsObject, fieldName: String)
  (implicit write: pack.Writer[JsObject], reader: pack.Reader[T], ec: ExecutionContext, rds: Reads[B]): Future[Seq[B]] =
    ctx(db).find(selector, QueryBuilder.fieldsProjection(fieldName)).cursor[JsValue]().collect[Seq]()
      .map { lst => lst.map(_ \ fieldName).map(_.as[B]) }

  def insert(db: DB, document: T, id: Long)(implicit selectorWriter: pack.Writer[JsObject], updateWriter: pack.Writer[T], reader: pack.Reader[T], ec: ExecutionContext): Future[Option[T]] =
    insert(db, document, QueryBuilder.withId(id))

  private def insert(db: DB, document: T, validator: JsObject)(implicit selectorWriter: pack.Writer[JsObject], updateWriter: pack.Writer[T], reader: pack.Reader[T], ec: ExecutionContext): Future[Option[T]] =
    ctx(db).insert(document).flatMap { _ => one(db, validator) }

  def update(db: DB, selector: JsObject, update: T)(implicit selectorWriter: pack.Writer[JsObject], updateWriter: pack.Writer[T], ec: ExecutionContext): Future[UpdateWriteResult] =
    ctx(db).update(selector, update, upsert = false, multi = true)

  def edit(db: DB, id: Long, update: T)(implicit selectorWriter: pack.Writer[JsObject], updateWriter: pack.Writer[T], ec: ExecutionContext): Future[UpdateWriteResult] =
    ctx(db).update(QueryBuilder.withId(id), update, upsert = false, multi = false)
}