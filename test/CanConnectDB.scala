import com.github.athieriot.EmbedConnection
import org.specs2.mutable.Specification
import org.specs2.specification.Before
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsObject, Json}
import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection.JSONCollection

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Awaitable}

/**
  * 定义了一个原始数据库连接的测试辅类
  */
class CanConnectDB extends Specification
  with EmbedConnection
  with Before {

  sequential
  /**
    * 测试数据库链接
    */
  val db: reactivemongo.api.DefaultDB = {
    val driver = new reactivemongo.api.MongoDriver
    val connect = driver.connection("localhost:27017" :: Nil)
    connect("bolero_db_dev")
  }

  /**
    * 默认的测试超时时间阀值
    */
  val duration = Duration(10000, "millis")

  /**
    * 在规定的时间内同步等待Async执行完毕
    */
  def waitIt[T](awaitable: Awaitable[T]): T = Await.result(awaitable, duration)

  def waitItAsSome[T](awaitable: Awaitable[Option[T]]): T = {
    val it = waitIt(awaitable)
    it must be some

    it.get
  }

  def waitItAsNone[T](awaitable: Awaitable[Option[T]]): Unit = {
    val it = waitIt(awaitable)
    it must be none
  }

  def ctx(collectionName: String): JSONCollection = db.collection[JSONCollection](collectionName)

  def ctx(collectionName: Symbol): JSONCollection = ctx(collectionName.name)

  val emptyQuery = Json.obj()

  private def collectionSize(collectionName: String, query: JsObject): Int =
    waitIt(ctx(collectionName).count(Some(query)))

  /**
    * 指定的文档大小（行数）
    *
    * @param collectionName 文档名称
    * @param query          查询参数
    * @return 返回符合查询参数的文档行数
    */
  def collectionSize(collectionName: Symbol, query: JsObject = emptyQuery): Int =
    collectionSize(collectionName.name, query)

  protected def collectionPropertyReset
  (collectionName: Symbol,
   query: JsObject,
   p: String,
   set: JsValueWrapper): Boolean = {
    val future = ctx(collectionName).update(
      query,
      Json.obj("$set" -> Json.obj(p -> set)),
      multi = true,
      upsert = false
    )

    waitIt(future).ok
  }

  /**
    * 删除指定的文档
    *
    * @param collectionName 文档名称
    * @param query          查询参数
    * @return 当且仅当操作成功执行之后，返回True，否则为False
    */
  def collectionDelete(collectionName: Symbol, query: JsObject = Json.obj()): Boolean =
    waitIt(ctx(collectionName.name).remove(query)).ok

  override def before: Any = {
    ok
  }
}

