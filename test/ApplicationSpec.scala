import biz.{AuthBiz, UserProfileBiz}
import base.{modelStatus, mongo}
import base.mongo.userFields.IdentityType
import models._
import models.interop.{HTTPResponse, HTTPResponseError, WeChatUserInfo}
import org.specs2.runner._
import org.junit.runner._
import play.api.libs.json._
import play.api.mvc.Result
import play.api.test._
import play.api.test.Helpers._

import scala.concurrent.Future

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends StaticDataConnectionSpec {
  sequential

  protected case class Uri(method: String, uri: String, auth: Boolean = true) {
    def withSimpleQuery(key: String, value: Any): Uri =
      Uri(method, uri.concat(s"?$key=$value"), auth)

    def withId(id: String): Uri =
      Uri(method, uri.replace(":id", id), auth)
  }

  protected object routes {
    val GET_ADDRESSES = Uri("GET", "/user/addresses")
    val GET_ADDRESS = Uri("GET", "/user/address")
    val GET_USER = Uri("GET", "/user")
    val GET_ORDER_LIST = Uri("GET", "/user/orders")
    val GET_TYPIES = Uri("GET", "/static/goods/type", auth = false)
    val GET_CART = Uri("GET", "/user/cart")
    val GET_GOODS = Uri("GET", "/goods", auth = false)
    val GET_GOODS_INDEX = Uri("GET", "/goods/index", auth = false)

    val GET_ORDER = Uri("GET", "/order/:id")
    val GET_ORDER_FLOW = Uri("GET", "/order/:id/flow")
    val GET_ORDER_STATUS = Uri("GET", "/order/:id/flow/asLatest")
    val POST_ORDER_FINISH = Uri("POST", "/order/:id/finish")

    // static rules
    val GET_SHIPPING_RULE = Uri("GET", "/static/rules/shipping", auth = false)

    // POST
    val POST_ADDRESS = Uri("POST", "/user/address")
    val POST_CART = Uri("POST", "/user/cart")
    val POST_CHECKOUT = Uri("POST", "/checkout")
    val POST_AUTH_APPLY = Uri("POST", "/auth/apply", auth = false)
    val POST_PAYMENT_APPLY = Uri("POST", "/pay/apply")

    // injection
    val WEB_HOOK_PINGXX = Uri("POST", "/inject/pingxx", auth = false)
  }

  /**
    * 校验ResponseResult是否符合我们的models.HTTPResponse类型
    * 并从其中Extract出我们关注的data
    */
  protected def contentValidate[T](content: Future[Result])(implicit rds: Reads[T]): T = {
    contentAsString(content) must contain("{\"ok\":")
    contentType(content) must beSome.which(_ == "application/json")
    val json = contentAsJson(content)

    (json \ "error").as[String] === ""
    (json \ "ok").as[Boolean] must beTrue
    (json \ "data").asOpt[JsValue] must beSome[JsValue]

    (json \ "status").asOpt[String] must beNone
    (json \ "message").asOpt[String] must beNone

    val response = json.validate[HTTPResponse].get
    response.ok must beTrue

    val target = response.data.validate[T]
    target must beAnInstanceOf[JsSuccess[T]]
    target.get
  }

  protected def contentError(content: Future[Result], error: HTTPResponseError) = {
    contentType(content) must beSome.which(_ == "application/json")
    val json = contentAsJson(content)

    (json \ "ok").as[Boolean] must beFalse
    (json \ "error").as[String] must not be empty
    // TODO (json \ "data").asOpt[JsValue] must beNone

    (json \ "status").asOpt[String] must beNone
    (json \ "message").asOpt[String] must beNone

    val response = json.validate[HTTPResponse].get
    response.ok must beFalse
    response.error._id must be equalTo error._id
  }

  lazy val TOKEN_QUERY_KEY = base.runtime.HTTP_AUTH2_HEADER_KEY

  private def buildQuery(query: Map[String, Seq[String]]): String = {
    val helper = scala.collection.mutable.ListBuffer[(String, String)]()
    query.foreach{ case (queryKey, v) =>
      v.foreach { queryValue =>
        helper.append(queryKey -> queryValue)
      }
    }

    helper.map{case (k, v) => k.concat("=").concat(v)} mkString "&"
  }

  private def withQueries[A](http: FakeRequest[A], query: Map[String, Seq[String]]): FakeRequest[A] = {
    val targetQuery = base.seqMerge[(String, Seq[String])](
      http.queryString, query, key = _._1,
      mergeItem = (x, y) => {x._1 -> (x._2 ++ y._2)})
    FakeRequest(
      method = http.method,
      uri = http.path.concat("?").concat(buildQuery(targetQuery.toMap)),
      headers = http.headers,
      body = http.body,
      remoteAddress = http.remoteAddress,
      version = http.version,
      id = http.id,
      tags = http.tags,
      secure = http.secure
    )
  }

  private def withQuery[A](http: FakeRequest[A], query: Map[String, String]): FakeRequest[A] =
    withQueries(http, query.map{case (k, v) => k -> Seq(v)})

  protected def http(uri: Uri, payload: JsValue = JsNull, token: String = "") = uri match {
    case Uri("GET", link, true) => getAuthed(link, token)
    case Uri("GET", link, false) => get(link)
    case Uri("POST", link, true) => postAuthed(link, payload, token)
    case Uri("POST", link, false) => post(link, payload)
  }

  private def get(uri: String) = route(FakeRequest(GET, uri)).get
  private def getAuthed(uri: String, token: String) =
    route(FakeRequest(GET, uri).withHeaders(TOKEN_QUERY_KEY -> token)).get
  private def post(uri: String, payload: JsValue) = {
    val response = route(FakeRequest(POST, uri)
      .withJsonBody(payload)
      .withHeaders("Content-Type" -> "application/json"))

    response.get
  }
  private def postAuthed(uri: String, payload: JsValue, token: String) = {
    val response = route(FakeRequest(POST, uri)
      .withJsonBody(payload)
      .withHeaders(
        "Content-Type" -> "application/json",
        TOKEN_QUERY_KEY -> token
      ))

    response.get
  }

  protected object FakeUser {
    val n = 5

    val wechatOpenId1 = fakeWeChatId("kei930dke3k1ldlalei3")
    val wechatOpenId2 = fakeWeChatId("mvkd74kdklsl162ldle7")
    val wechatOpenId3 = fakeWeChatId("kekdikekl81kzzaaqkqk")
    val wechatOpenId4 = fakeWeChatId("pqlldkozkqdqzjqkkaik")
    val wechatOpenId5 = fakeWeChatId("wwkkwkkqooqokwkkekwk")


    // do not test 3rd party interop call
    private def fakeUserToken: String => String = (id: String) =>
      waitIt(
        UserProfileBiz
          .createUser(db, User.asWeChatUser(WeChatUserInfo(id, "nickname", 1, "SH")))
          .flatMap(AuthBiz.assignToken(db, _))
      )

    val token1 = fakeUserToken(wechatOpenId1)
    val token2 = fakeUserToken(wechatOpenId2)
    val token3 = fakeUserToken(wechatOpenId3)
    val token4 = fakeUserToken(wechatOpenId4)
    val token5 = fakeUserToken(wechatOpenId5)

    val tokens = Seq(token1, token2, token3, token4, token5)
  }

  "用户认证" should {
    "能够为多个用户分别分发认证Token" in new WithApplication {
      FakeUser.token1 must size(base.mongo.identitySize.AUTH_TOKEN)
      FakeUser.token2 must size(base.mongo.identitySize.AUTH_TOKEN)
      FakeUser.token3 must size(base.mongo.identitySize.AUTH_TOKEN)
      FakeUser.token4 must size(base.mongo.identitySize.AUTH_TOKEN)
      FakeUser.token5 must size(base.mongo.identitySize.AUTH_TOKEN)

      Seq(FakeUser.token1, FakeUser.token2, FakeUser.token3,
        FakeUser.token4, FakeUser.token5).distinct must have size FakeUser.n
    }
  }

  "测试前的数据初始化" should {
    collectionDelete(base.mongo.collectionName.ORDERS)

    "两个用户目前没有订单" in new WithApplication {
      contentValidate[Seq[Order]](http(routes.GET_ORDER_LIST, token = FakeUser.token1)) must beEmpty
      contentValidate[Seq[Order]](http(routes.GET_ORDER_LIST, token = FakeUser.token2)) must beEmpty
    }

    collectionPropertyReset(
      base.mongo.collectionName.USERS,
      Json.obj(),
      "shoppingCart",
      Json.arr()
    )

    "CartItem should be empty right now" in new WithApplication {
      contentValidate[Seq[CoreCartItem]](
        http(routes.GET_CART,
          token = FakeUser.token1)) must be size 0

      contentValidate[Seq[CoreCartItem]](
        http(routes.GET_CART,
          token = FakeUser.token2)) must be size 0
    }
  }

  "UI客户端" should {

    "send 404 on a bad request" in new WithApplication {
      route(FakeRequest(GET, "/boum")) must beSome.which (status(_) == NOT_FOUND)
    }

    "render the index page" in new WithApplication {
      val json = contentValidate[JsValue](get("/"))

      (json \ "revision").as[String] must not be empty
    }
  }

  "用户页面" should {
    "能够在认证状态下获得用户信息" in new WithApplication {
      val user1 = contentValidate[User](http(routes.GET_USER, token = FakeUser.token1))
      user1._id must not be empty

      contentError(http(routes.GET_USER, token = "boum"), HTTPResponseError.AUTH2_USER_NOT_AUTHENTICATED)
    }

    "能够获得邀请链接的用户识别码" in new WithApplication {
      contentValidate[String](get(s"/user/$userId/code")) must not be empty
    }
  }

  "购物车页面" should {
    "能够增加购物车的条目" in new WithApplication {
      contentValidate[Seq[CoreCartItem]](http(
        routes.POST_CART,
        payload = Json.arr(CoreCartItem(g1, 3, 1), CoreCartItem(g2, 4, 1)),
        token = FakeUser.token1)) must have size 2

      contentValidate[Seq[CoreCartItem]](http(
        routes.POST_CART,
        payload = Json.arr(CoreCartItem(g2, 3, 1)),
        token = FakeUser.token2)) must have size 1
    }

    "能够修改购物车的条目数量" in new WithApplication {
      // 获取当前的购物车数量
      val load = http(routes.GET_CART, token = FakeUser.token1)
      val cart = contentValidate[Seq[CoreCartItem]](load)
      val origin = cart.head
      val offset = -2
      val target = origin.offsetAmount(offset)

      // 更新购物车数量，保证offset成功
      val response = http(routes.POST_CART, payload = Json.arr(target), token = FakeUser.token1)
      contentValidate[Seq[CoreCartItem]](response)
      .find(_.goods._id == origin.goods._id)
      .get
      .goodsAmount === (origin.goodsAmount + offset)
    }

    "能够制止未授权的用户地址信息加载" in new WithApplication {
      contentError(
        http(routes.POST_CART, payload = Json.obj(), token = base.STRING_NIL),
        HTTPResponseError.AUTH2_USER_NOT_AUTHENTICATED
      )
    }
  }

  "支付" should {

    "Webhook可以接受到Ping++的收款通知" in new WithApplication {
      val nilOrderId = base.STRING_NIL
      val chargeJson = s"{'id':'evt_ugB6x3K43D16wXCcqbplWAJo','created':1440407501,'livemode':true,'type':'charge.succeeded','data':{'object':{'id':'ch_Xsr7u35O3m1Gw4ed2ODmi4Lw','object':'charge','created':1440407501,'livemode':true,'paid':true,'refunded':false,'app':'app_urj1WLzvzfTK0OuL','channel':'upacp','order_no':'$nilOrderId','client_ip':'127.0.0.1','amount':100,'amount_settle':0,'currency':'cny','subject':'Your Subject','body':'Your Body','extra':{},'time_paid':1440407501,'time_expire':1440407501,'time_settle':null,'transaction_no':'1224524301201505066067849274','refunds':{'object':'list','url':'/v1/charges/ch_Xsr7u35O3m1Gw4ed2ODmi4Lw/refunds','has_more':false,'data':[]},'amount_refunded':0,'failure_code':null,'failure_msg':null,'metadata':{},'credential':{},'description':null}},'object':'event','pending_webhooks':0,'request':'iar_qH4y1KbTy5eLGm1uHSTS00s'}".replace(''', '"')
      val chargeSuccessful = Json.parse(chargeJson)
      var callback = contentError(
        http(routes.WEB_HOOK_PINGXX, payload = chargeSuccessful),
        HTTPResponseError.MONGO_NOT_FOUNT()
      )

      val orders = contentValidate[Seq[Order]](http(routes.GET_ORDER_LIST, token = FakeUser.token2))
      orders must size(2)

      val order1 = orders.head
      val paidResponse = contentValidate[String](http(
        routes.WEB_HOOK_PINGXX,
        payload = Json.parse(chargeJson.replace(nilOrderId, order1._id))))
      paidResponse === s"${order1._id}: payment received"

      // 支付token1的订单
      val order2 = contentValidate[Seq[Order]](http(
        routes.GET_ORDER_LIST.withSimpleQuery("status", modelStatus.order.CREATED),
        token = FakeUser.token1)).head
      contentValidate[String](http(
        routes.WEB_HOOK_PINGXX,
        payload = Json.parse(chargeJson.replace(nilOrderId, order2._id)))
      ) === s"${order2._id}: payment received"
    }
  }

  "订单清单和状态查询" should {

    "获取用户的订单清单" in new WithApplication {
      val ordersOfUser1 = contentValidate[Seq[Order]](http(routes.GET_ORDER_LIST, token = FakeUser.token1))
      val ordersOfUser2 = contentValidate[Seq[Order]](http(routes.GET_ORDER_LIST, token = FakeUser.token2))
      ordersOfUser1 must not be empty
      ordersOfUser2 must not be empty
    }

    "获取指定的订单" in new WithApplication {
      def f(status: Int, token: String): Seq[Order] = contentValidate[Seq[Order]](http(
        routes.GET_ORDER_LIST.withSimpleQuery("status", status),
        token = token))
      def g(id: String, token: String): Order = contentValidate[Order](http(
        routes.GET_ORDER.withId(id), token = token))
      def all(status: Int, token: String) =
        f(status, token).forall(order => g(order._id, token)._id == order._id) must beTrue

      all(modelStatus.order.CREATED, FakeUser.token1)
      all(modelStatus.order.PAID, FakeUser.token1)
      all(modelStatus.order.DELIVERED, FakeUser.token1)
      all(modelStatus.order.CREATED, FakeUser.token2)
      all(modelStatus.order.PAID, FakeUser.token2)
      all(modelStatus.order.DELIVERED, FakeUser.token2)
    }

    "获取指定订单的最新状态" in new WithApplication {
      def f(status: Int, token: String): Seq[Order] = contentValidate[Seq[Order]](http(
        routes.GET_ORDER_LIST.withSimpleQuery("status", status),
        token = token))
      def g(id: String, token: String): OrderFlow = contentValidate[OrderFlow](http(
        routes.GET_ORDER_STATUS.withId(id), token = token))
      def all(status: Int, token: String) =
        f(status, token)
          .forall(order =>
            g(order._id, token).status == status) must beTrue

      all(modelStatus.order.CREATED, FakeUser.token1)
      all(modelStatus.order.PAID, FakeUser.token1)
      all(modelStatus.order.DELIVERED, FakeUser.token1)
      all(modelStatus.order.CREATED, FakeUser.token2)
      all(modelStatus.order.PAID, FakeUser.token2)
      all(modelStatus.order.DELIVERED, FakeUser.token2)
    }

    "获取指定订单的状态清单" in new WithApplication {
      def f(token: String): Seq[Order] = contentValidate[Seq[Order]](http(
        routes.GET_ORDER_LIST, token = token))
      def g(id: String, token: String): Seq[OrderFlow] = contentValidate[Seq[OrderFlow]](
        http(routes.GET_ORDER_FLOW.withId(id), token = token))
      def all(token: String) = f(token).forall { order =>
        val flow = g(order._id, token)
        flow must not be empty
        val flowByDate = flow.sortBy(_.atSeconds)
        val flowByStat = flow.sortBy(_.status)

        flowByDate.zip(flowByStat).forall {
          case (a, b) => a.atSeconds == b.atSeconds && a.status == b.status
          case _ => false
        }
      } must beTrue

      all(FakeUser.token1)
      all(FakeUser.token2)
    }
  }

  def byId(id: String): Seq[TuanGoods] =
    contentValidate[Seq[TuanGoods]](get(s"/tuan/goods/$id"))

  def randomSeriesTuan: Seq[TuanGoods] = {
    val ts = contentValidate[Seq[TuanGoods]](get("/tuan/goods"))
    val i = scala.util.Random.nextInt(ts.length)
    val rootGoods = ts(i)

    byId(rootGoods._id)
  }
}
