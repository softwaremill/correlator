package com.softwaremill.correlator

import java.util.concurrent.ConcurrentLinkedQueue

import monix.eval.Task
import org.http4s._
import org.http4s.dsl.Http4sDsl
import monix.execution.Scheduler.Implicits.global
import org.slf4j.{Logger, LoggerFactory}
import org.http4s.implicits._
import scala.collection.JavaConverters._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import Http4sCorrelationMiddleware._

class CorrelationIdDecoratorTest extends AnyFlatSpec with Matchers {
  TestCorrelationIdDecorator.init()

  private val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  private val dsl = Http4sDsl[Task]
  import dsl._

  "CorrelationId" should "create and pass to downstream http requests" in new Fixture {
    // given
    val request = Request[Task](method = GET, uri = uri"/test")

    // when
    val response =
      Http4sCorrelationMiddleware(TestCorrelationIdDecorator).withCorrelationId(routes).apply(request).value.runSyncUnsafe().get

    //then
    response.status shouldBe Status.Ok

    seenCids.asScala.toList should have size (1)
    seenCids.asScala.toList.foreach(_ shouldBe Symbol("defined"))
  }

  it should "use correlation id from the header if available" in new Fixture {
    // given
    val testCid = "some-cid"
    val request =
      Request[Task](method = GET, uri = uri"/test", headers = Headers.of(Header(Http4sCorrelationMiddleware.HeaderName, testCid)))

    // when
    val response =
      Http4sCorrelationMiddleware(TestCorrelationIdDecorator).withCorrelationId(routes).apply(request).value.runSyncUnsafe().get

    //then
    response.status shouldBe Status.Ok

    seenCids.asScala.toList shouldBe List(Some(testCid))
  }

  trait Fixture {
    val seenCids = new ConcurrentLinkedQueue[Option[String]]()

    val routes: HttpRoutes[Task] = HttpRoutes.of[Task] { case _ =>
      TestCorrelationIdDecorator().asyncBoundary
        .flatMap(cid => Task.eval(seenCids.add(cid)))
        .flatMap(_ => Task.eval(logger.info("Hello!")))
        .map(_ => Response[Task](Status.Ok))
    }
  }
}

object TestCorrelationIdDecorator extends CorrelationIdDecorator()
