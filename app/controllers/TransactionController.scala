package controllers

import javax.inject.Inject

import biz._
import models.TransactionPayload
import org.joda.time.DateTime
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{Json, _}
import play.api.mvc.{Action, Controller, Result}
import play.modules.reactivemongo.json._

import scala.concurrent.Future

// Reactive Mongo imports
import play.modules.reactivemongo.{MongoController, ReactiveMongoApi, ReactiveMongoComponents}

class TransactionController @Inject() (val reactiveMongoApi: ReactiveMongoApi)
  extends Controller
    with MongoController with ReactiveMongoComponents {

  def create(id: Long) = Action.async(parse.json) { request =>
    request.body.validate[TransactionPayload]
      .map { payload =>
        TransactionBiz.one(db, id).flatMap {
          case None => TransactionBiz.insert(db, payload.asTransaction(id), id).map(_ => Ok(Json.obj("status"->"ok")))
          case Some(_) => Future.successful(Ok(Json.obj("status" -> "data _id duplicated")))
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

  def corsGET(result: Result): Result =
    result.withHeaders("Access-Control-Allow-Origin" -> "*")

  def corsPOST(result: Result): Result =
    corsGET(result).withHeaders(
      "Access-Control-Allow-Headers" -> s"Content-Type",
      "Access-Control-Allow-Methods" -> "POST, OPTIONS",
      "Access-Control-Max-Age" -> "600")

  def corsOPTION(from: String = "...") = Ok

  def preFlight(path: String) = Action { request =>
    corsOPTION(path)
  }


  def meta = Action.async {
    Future.successful (Json.obj(
      "revision" -> "8134a3a",
      "release" -> new DateTime(2016, 7, 27, 7, 48).toString("yyyy-MM-dd HH:mm")
    )).map(Ok(_)).map(corsGET)
  }
}