package com.softwaremill.correlator

import java.{util => ju}

import cats.data.{Kleisli, OptionT}
import ch.qos.logback.classic.util.LogbackMDCAdapter
import org.http4s.util.CaseInsensitiveString
import org.http4s.{HttpRoutes, Request, Response}
import org.slf4j.{Logger, LoggerFactory, MDC}
import zio.random.Random
import zio.{FiberRef, Runtime, Task, UIO, ZIO}

/**
  * Correlation id support. The `init()` method should be called when the application starts.
  * See [[https://blog.softwaremill.com/correlation-ids-in-scala-using-monix-3aa11783db81]] for details.
  */
final class CorrelationIdMiddleware private[correlator] (val headerName: String, logStartRequest: (String, Request[Task]) => Task[Unit]) {
  import CorrelationIdMiddleware._
  import cats.implicits._
  import zio.interop.catz._

  def addTo[R <: Random](service: HttpRoutes[Task])(implicit runtime: Runtime[R]): HttpRoutes[Task] =
    Kleisli { req: Request[Task] =>
      val cidM = req.headers.get(CaseInsensitiveString(headerName)).fold(newCorrelationId(runtime.environment))(_.value.pure[UIO])

      val setupAndService: Task[Option[Response[Task]]] =
        for {
          cid <- cidM
          _   <- Task(MDC.put(MdcKey, cid))
          _   <- logStartRequest(cid, req)
          r   <- service(req).value
        } yield r

      OptionT(setupAndService.ensuring(Task(MDC.remove(MdcKey)).orElse(ZIO.unit)))
    }

  private def newCorrelationId(random: Random): UIO[String] = {
    val randomUpperCaseChar: UIO[Char] = random.random.nextInt(91 - 65).map(r => (r + 65).toChar)
    val segment: UIO[String] = (1 to 3).toList.traverse(_ => randomUpperCaseChar).map(_.mkString)
    (segment, segment, segment).mapN((a, b, c) => s"$a-$b-$c")
  }
}

object CorrelationIdMiddleware {
  private[correlator] final val MdcKey: String = "cid"
  private[correlator] final val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  final val defaultHeaderName: String = "X-Correlation-ID"
  final val defaultLogStartRequest: (String, Request[Task]) => Task[Unit] = (cid, req) =>
    Task(logger.debug(s"TOTO - Starting request with id: $cid, to: ${req.uri.path}"))

  def init(implicit runtime: Runtime[_]): Task[CorrelationIdMiddleware] = init(defaultHeaderName, defaultLogStartRequest)

  def init(headerName: String, logStartRequest: (String, Request[Task]) => Task[Unit])(implicit runtime: Runtime[_]): Task[CorrelationIdMiddleware] =
    for {
      fiber <- FiberRef.make[ju.Map[String, String]](new ju.HashMap())
      _ <- Task {
        val field = classOf[MDC].getDeclaredField("mdcAdapter")
        field.setAccessible(true)
        field.set(null, new ZioMDCAdapter(fiber))
      }
    } yield new CorrelationIdMiddleware(headerName, logStartRequest)

  val getCorrelationId: Task[Option[String]] = Task(Option(MDC.get(MdcKey)))
}

/**
  * Based on [[https://olegpy.com/better-logging-monix-1/]]. Makes the current correlation id available for logback
  * loggers.
  */
final class ZioMDCAdapter[+R](fiber: FiberRef[ju.Map[String, String]])(implicit runtime: Runtime[R]) extends LogbackMDCAdapter {
  override def get(key: String): String = runtime.unsafeRun { fiber.get.map(_.get(key)) }

  override def put(key: String, `val`: String): Unit =
    runtime.unsafeRun {
      fiber.modify { map =>
        map.put(key, `val`)
        ((), map)
      }
    }

  override def remove(key: String): Unit =
    runtime.unsafeRun {
      fiber.modify { map =>
        map.remove(key)
        ((), map)
      }
    }

  override def clear(): Unit = runtime.unsafeRun { fiber.set(new ju.HashMap()) }

  override def getCopyOfContextMap: ju.HashMap[String, String] = runtime.unsafeRun { fiber.get.map(new ju.HashMap(_)) }

  override def setContextMap(contextMap: ju.Map[String, String]): Unit =
    runtime.unsafeRun { fiber.set(new ju.HashMap(contextMap)) }

  override def getPropertyMap: ju.Map[String, String] = runtime.unsafeRun { fiber.get }

  override def getKeys: ju.Set[String] = runtime.unsafeRun { fiber.get.map(_.keySet()) }
}
