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

  def ctx(collectionName: String): JSONCollection = db.collection[JSONCollection](collectionName)

  def ctx(collectionName: Symbol): JSONCollection = ctx(collectionName.name)

  val emptyQuery = Json.obj()

  private def collectionSize(collectionName: String, query: JsObject): Int =
    waitIt(ctx(collectionName).count(Some(query)))

  def collectionDelete(collectionName: Symbol, query: JsObject = Json.obj()): Boolean =
    waitIt(ctx(collectionName.name).remove(query)).ok

  override def before: Any = {
    ok
  }
}

