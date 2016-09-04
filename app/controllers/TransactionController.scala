package controllers

import biz._
import models._
import models.interop.payload.TransactionPayload
import models.interop.{HTTPResponse, HTTPResponseError}
import org.joda.time.DateTime
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._
import scala.concurrent.Future
import play.modules.reactivemongo.MongoController

import models.interop.HTTPResponseError
import play.api.libs.json.{JsValue, Json}
import play.modules.reactivemongo.json._
import reactivemongo.api.DB

import scala.concurrent.{ExecutionContext, Future}

import javax.inject.Inject

import scala.concurrent.Future

import play.api.Logger
import play.api.mvc.{ Action, Controller }
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._

// Reactive Mongo imports
import reactivemongo.api.{DB, Cursor}

import play.modules.reactivemongo.{ // ReactiveMongo Play2 plugin
MongoController,
ReactiveMongoApi,
ReactiveMongoComponents
}

class TransactionController @Inject() (val reactiveMongoApi: ReactiveMongoApi)
  extends Controller
    with MongoController with ReactiveMongoComponents
    with CanResponse
    with CanCrossOrigin {

  def create(id: Long) = Action.async(parse.json) { request =>
    request.body.validate[TransactionPayload]
      .map { payload =>
        TransactionBiz.one(db, id).flatMap {
          case None => TransactionBiz.insert(db, payload.asTransaction(id), id).map(_ => Ok(Json.obj("status"->"ok")))
          case Some(_) => base.fs(ResponseError(HTTPResponseError.MONGO_ID_DUPLICATED))
        }
      }
      .recoverTotal { e => Future.successful(BadRequest(JsError.toJson(e))) }
      .map (corsPOST)
  }

  def get(id: Long) = Action.async { request =>
    TransactionBiz.one(db, id).map {
      case None => Ok(Json.obj())
      case Some(tx) => Ok(Json.toJson(tx.asPayload))
    }.map (corsGET)
  }

  def list(tp: String) = Action.async { request =>
    TransactionBiz.sequence[Long](db, Json.obj("type" -> tp), "_id")
      .map(lst => Ok(Json.toJson(lst)))
      .map(corsGET)
  }

  def sum(id: Long) = Action.async { request =>
    TransactionBiz.sequence[Double](db, QueryBuilder.or(QueryBuilder.withId(id), Json.obj("parent_id" -> id)), "amount")
      .map(lst => lst.sum)
      .map(sum => Ok(Json.obj("sum" -> sum)))
      .map(corsGET)
  }

  def index = Action.async { request =>
    TransactionBiz.list(db)
      .map(lst => Ok(Json.toJson(lst)))
      .map(corsGET)
  }
}