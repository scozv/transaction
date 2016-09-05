package base

import play.modules.reactivemongo.json.JSONSerializationPack
import play.modules.reactivemongo.json.collection.JSONCollection
import reactivemongo.api.DB

import scala.concurrent.{ExecutionContext, Future}

object mongo {
  object generalFields {
    val hierarchicId = "rootId"
  }

  def ctx(db: DB, collectionName: Symbol): JSONCollection = ctx(db, collectionName.name)

  private def ctx(db: DB, collectionName: String): JSONCollection =
    db.collection[JSONCollection](collectionName)

}
