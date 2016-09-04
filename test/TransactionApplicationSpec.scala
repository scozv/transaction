import play.api.test.WithApplication
import models._
import models.interop.HTTPResponseError
import models.interop.payload.TransactionPayload
import play.api.libs.json.Json

class TransactionApplicationSpec extends CanFakeHTTP {
  sequential

  "data clear up" should {
    "clear previous tx data" in {
      collectionDelete('transactions) must beTrue
    }
  }

  "PUT /:id" should {
    "create new tx via PUT"                             in a1
    "be able to create multi tx"                        in a2
    "return error when duplicated tx creating"          in a3
  }

  "GET /:id" should {
    "return specific tx via GET"                        in b1
    "return NOT_FOUND with non-existing tx id"          in b2
  }

  "GET /type" should {
    "return a list of tx id"                            in c1
    "check each tx matched specific :type or not"       in c2
  }

  "GET /sum/:id" should {
    "return 0 when :id not existing"                    in d1
    "return valid sum calculation"                      in d2
    "check sum of each :id"                             in d3
  }


  // prepare tx data with id from 1 to 5
  val txData =
    Transaction("1", 100, "cars") ::
    Transaction("2", 200, "food") ::
    Transaction("3", 300, "cars", Some("2")) ::
    Transaction("4", 750, "digital") ::
    Transaction("5", 1000, "shopping", Some("2")) :: Nil

  object routes {
    val PUT_TX = Uri("PUT", "/transactionservice/transaction/:id", auth = false)
    val GET_TX = Uri("GET", "/transactionservice/transaction/:id", auth = false)
    val GET_BY_TYPE = Uri("GET", "/transactionservice/types/:tp", auth = false)
    val SUM = Uri("GET", "/transactionservice/sum/:id", auth = false)
  }

  def a1 = new WithApplication {
    // create new tx via PUT

    // 0. put tx with id 1
    val tx = txData.find(_._id == "1").get
    val response = http(routes.PUT_TX.withId("1"), payload = Json.toJson(tx))
    // 0. check {status -> ok}
    jsonValidate(response, "status", "ok")
  }

  def a2 = new WithApplication {
    // be able to create multi tx

    // 0. for 2 to 5
    txData.filter(_._id > "1").foreach { tx =>
      // 0. put each tx
      val response = http(routes.PUT_TX.withId(tx._id), payload = Json.toJson(tx))
      // 0. check {status -> ok}
      jsonValidate(response, "status", "ok")

    }
  }

  def a3 = new WithApplication {
    // return error when duplicated tx creating

    // 0. put tx with id 1 (duplicated)
    val tx = txData.find(_._id == "1").get
    val response = http(routes.PUT_TX.withId("1"), payload = Json.toJson(tx))
    // 0. error return
    contentError(response, HTTPResponseError.MONGO_ID_DUPLICATED)
  }

  def b1 = new WithApplication {
    // return specific tx via GET

    // 0. get tx with id 1
    val response = http(routes.GET_TX.withId("1"))
    // 0. check properties of tx with id 1
    jsonValidate(response, "type", "cars")
    jsonValidate(response, "amount", 100)
    jsonEmpty(response, Some("parent_id"))
  }

  def b2 = new WithApplication {
    // return {} with non-existing tx id

    // 0. get tx with id 1024 (non existing)
    val response = http(routes.GET_TX.withId("1024"))
    // 0. NOT_FOUND error
    jsonEmpty(response)
  }

  def c1 = new WithApplication {
    // return a list of tx id

    // 0. get type/:tp
    val response = http(routes.GET_BY_TYPE.withId("cars", ":tp"))
    val lst = jsonArray[String](response, List("1", "3"))
    lst must be size 2
    // 0. list.forAll (_.type must be same as :tp)
    lst.foreach { id =>
      jsonValidate(http(routes.GET_TX.withId(id)), "type", "cars")
    }
  }

  def c2 = new WithApplication {
    jsonArray[String](
      http(routes.GET_BY_TYPE.withId("food", ":tp")), List("2")) must be size 1

    jsonArray[String](
      http(routes.GET_BY_TYPE.withId("digital", ":tp")), List("4")) must be size 1

    jsonArray[String](
      http(routes.GET_BY_TYPE.withId("shopping", ":tp")), List("5")) must be size 1

    // return [] when type is not existed
    jsonArray[String](
      http(routes.GET_BY_TYPE.withId("404", ":tp")), Nil) must beEmpty
  }


  def d1 = new WithApplication {
    // return 0 when :id not existing
    jsonValidate[Double](http(routes.SUM.withId("1024")), "sum", 0.0)
  }

  def d2 = new WithApplication {
    // return valid sum calculation

    // 0. get the sum from RESTful api
    val response = http(routes.SUM.withId("2"))
    // 0. sum the prepared data
    val origin = txData.filter(x => x._id == "2" || x.rootId == "2").map(_.amount).sum
    // 0. must be equal
    jsonValidate[Double](response, "sum", origin)
  }

  def d3 = new WithApplication {
    // check sum of each :id

    List("1", "3", "4", "5").foreach { id =>
      val response = http(routes.SUM.withId(id))
      val origin = txData.filter(x => x._id == id || x.rootId == id).map(_.amount).sum
      jsonValidate[Double](response, "sum", origin)
    }
  }
}
