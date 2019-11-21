package com.softwaremill.correlator

import java.{ util => ju }

import cats.data.{ Kleisli, OptionT }
import ch.qos.logback.classic.util.LogbackMDCAdapter
import com.github.ghik.silencer.silent
import org.http4s.util.CaseInsensitiveString
import org.http4s.{ HttpRoutes, Request, Response }
import org.slf4j.{ Logger, LoggerFactory, MDC }
import zio.macros.annotation.accessible
import zio.random.Random
import zio.{ FiberRef, RIO, Runtime, Task, UIO, URIO, ZIO }

object CorrelationIdMiddleware {
  import cats.implicits._
  import zio.interop.catz._

  private[correlator] final val MdcKey: String = "cid"
  private[correlator] final val logger: Logger = LoggerFactory.getLogger(getClass.getName)

  final val defaultHeaderName: String = "X-Correlation-ID"

  final val defaultLogStartRequest: (String, Request[Task]) => Task[Unit] = (cid, req) =>
    Task(logger.debug(s"Starting request with id: $cid, to: ${req.uri.path}"))

  final val defaultIdGenerator: URIO[Random, String] =
    ZIO.accessM[Random] { random =>
      val randomUpperCaseChar: UIO[Char] = random.random.nextInt(91 - 65).map(r => (r + 65).toChar)
      val segment: UIO[String]           = (1 to 3).toList.traverse(_ => randomUpperCaseChar).map(_.mkString)
      (segment, segment, segment).mapN((a, b, c) => s"$a-$b-$c")
    }

  final val getCorrelationId: Task[Option[String]] = Task(Option(MDC.get(MdcKey)))

  @silent("parameter value zioMDCAdapter in method addTo is never used")
  def addTo[R <: Random with ZioMDC](
    headerName: String = defaultHeaderName,
    logStartRequest: (String, Request[Task]) => Task[Unit] = defaultLogStartRequest,
    idGenerator: RIO[R, String] = defaultIdGenerator
  )(service: HttpRoutes[Task]): HttpRoutes[Task] =
    Kleisli { req: Request[Task] =>
      val cidM: Task[String] =
        req.headers.get(CaseInsensitiveString(headerName)).fold(idGenerator.asInstanceOf[Task[String]])(_.value.pure[Task])

      val setupAndService: Task[Option[Response[Task]]] =
        for {
          cid <- cidM
          _   <- ZIO(MDC.put(MdcKey, cid))
          _   <- logStartRequest(cid, req)
          r   <- service(req).value
        } yield r

      OptionT(setupAndService.ensuring(Task(MDC.remove(MdcKey)).orElse(URIO.unit)))
    }
}

/**
 * Based on [[https://olegpy.com/better-logging-monix-1/]]. Makes the current correlation id available for logback
 * loggers.
 */
final class ZioMDCAdapter private[correlator] (fiber: FiberRef[ju.Map[String, String]], runtime: Runtime[_]) extends LogbackMDCAdapter {
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

object ZioMDCAdapter {
  final val init: Task[ZioMDCAdapter] =
    for {
      runtime <- ZIO.runtime[Any]
      fiber   <- FiberRef.make[ju.Map[String, String]](new ju.HashMap())
    } yield {
      val adapter = new ZioMDCAdapter(fiber, runtime)
      val field   = classOf[MDC].getDeclaredField("mdcAdapter")
      field.setAccessible(true)
      field.set(null, adapter)
      adapter
    }
}

trait ZioMDC {
  def mdc: ZioMDC.Service
}
object ZioMDC {
  trait Service {
    def get(key: String): String
    def put(key: String, `val`: String): Unit
    def remove(key: String): Unit
    def clear(): Unit
    def getCopyOfContextMap: ju.HashMap[String, String]
    def setContextMap(contextMap: ju.Map[String, String]): Unit
    def getPropertyMap: ju.Map[String, String]
    def getKeys: ju.Set[String]
  }

  trait ZioMDCLive extends ZioMDC {
    val runtime: Runtime[Any]

    @accessible
    final val newsletter: Service = new ZioMDC.Service {
      override def get(key: String): String = ???

      override def put(key: String, `val`: String): Unit = ???

      override def remove(key: String): Unit = ???

      override def clear(): Unit = ???

      override def getCopyOfContextMap: ju.HashMap[String, String] = ???

      override def setContextMap(contextMap: ju.Map[String, String]): Unit = ???

      override def getPropertyMap: ju.Map[String, String] = ???

      override def getKeys: ju.Set[String] = ???
    }
  }
}
