package com.softwaremill.correlator

import java.util.concurrent.ConcurrentLinkedQueue

import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.scalatest.{FlatSpec, Matchers}
import org.slf4j.{Logger, LoggerFactory}
import zio.{DefaultRuntime, Task}

import scala.collection.JavaConverters._

class CorrelationIdTest extends FlatSpec with Matchers {
  import org.http4s.implicits._

  private val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  private val dsl = Http4sDsl[Task]
  import dsl._

  "CorrelationId" should "create and pass to downstream http requests" in new Fixture {
    // given
    val request = Request[Task](method = GET, uri = uri"/test")

    // when
    val response = runtime.unsafeRun(correlationId.setCorrelationIdMiddleware(routes).apply(request).value).get

    //then
    response.status shouldBe Status.Ok

    seenCids.asScala.toList should have size (1)
    seenCids.asScala.toList.foreach(_ shouldBe Symbol("defined"))
  }

  it should "use correlation id from the header if available" in new Fixture {
    // given
    val testCid = "some-cid"
    val request =
      Request[Task](method = GET, uri = uri"/test", headers = Headers.of(Header(correlationId.headerName, testCid)))

    // when
    val response = runtime.unsafeRun(correlationId.setCorrelationIdMiddleware(routes).apply(request).value).get

    //then
    response.status shouldBe Status.Ok

    seenCids.asScala.toList shouldBe List(Some(testCid))
  }

  trait Fixture {
    import zio.interop.catz._

    val seenCids = new ConcurrentLinkedQueue[Option[String]]()
    implicit val runtime: DefaultRuntime = new DefaultRuntime {}
    val correlationId: CorrelationId = new CorrelationId()

    val routes: HttpRoutes[Task] = HttpRoutes.of[Task] {
      case _ =>
        correlationId
          .get
          .flatMap(cid => Task(seenCids.add(cid)))
          .flatMap(_ => Task(logger.info("Hello!")))
          .map(_ => Response[Task](Status.Ok))
    }
  }
}
