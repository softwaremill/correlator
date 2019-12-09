package com.softwaremill.correlator

import java.{util => ju}

import cats.data.{Kleisli, OptionT}
import ch.qos.logback.classic.util.LogbackMDCAdapter
import monix.eval.Task
import monix.execution.misc.Local
import org.http4s.util.CaseInsensitiveString
import org.http4s.{HttpRoutes, Request}
import org.slf4j.{Logger, LoggerFactory, MDC}

import scala.util.Random

/**
  * Correlation id support. The `init()` method should be called when the application starts.
  * See [[https://blog.softwaremill.com/correlation-ids-in-scala-using-monix-3aa11783db81]] for details.
  */
class CorrelationId(
    val headerName: String = "X-Correlation-ID",
    logStartRequest: (String, Request[Task]) => Task[Unit] = (cid, req) =>
      Task(CorrelationId.logger.debug(s"Starting request with id: $cid, to: ${req.uri.path}"))
) {
  System.setProperty("monix.environment.localContextPropagation", "1")
  def init(): Unit = {
    MonixMDCAdapter.init()
  }

  private val MdcKey = "cid"

  def apply(): Task[Option[String]] = Task(Option(MDC.get(MdcKey)))

  def applySync(): Option[String] = Option(MDC.get(MdcKey))

  def setCorrelationIdMiddleware(service: HttpRoutes[Task]): HttpRoutes[Task] = Kleisli { req: Request[Task] =>
    val cid = req.headers.get(CaseInsensitiveString(headerName)) match {
      case None            => newCorrelationId()
      case Some(cidHeader) => cidHeader.value
    }

    val setupAndService = for {
      _ <- Task(MDC.put(MdcKey, cid))
      _ <- logStartRequest(cid, req)
      r <- service(req).value
    } yield r

    OptionT(setupAndService.guarantee(Task(MDC.remove(MdcKey))))
  }

  private val random = new Random()

  private def newCorrelationId(): String = {
    def randomUpperCaseChar() = (random.nextInt(91 - 65) + 65).toChar
    def segment = (1 to 3).map(_ => randomUpperCaseChar()).mkString
    s"$segment-$segment-$segment"
  }
}

object CorrelationId {
  val logger: Logger = LoggerFactory.getLogger(getClass.getName)
}

/**
  * Based on [[https://olegpy.com/better-logging-monix-1/]]. Makes the current correlation id available for logback
  * loggers.
  */
class MonixMDCAdapter extends LogbackMDCAdapter {
  private[this] val map = Local[ju.Map[String, String]](ju.Collections.emptyMap())

  override def put(key: String, `val`: String): Unit = {
    if (map() eq ju.Collections.EMPTY_MAP) {
      map := new ju.HashMap()
    }
    map().put(key, `val`)
    ()
  }

  override def get(key: String): String = map().get(key)
  override def remove(key: String): Unit = {
    map().remove(key)
    ()
  }

  // Note: we're resetting the Local to default, not clearing the actual hashmap
  override def clear(): Unit = map.clear()
  override def getCopyOfContextMap: ju.Map[String, String] = new ju.HashMap(map())
  override def setContextMap(contextMap: ju.Map[String, String]): Unit =
    map := new ju.HashMap(contextMap)

  override def getPropertyMap: ju.Map[String, String] = map()
  override def getKeys: ju.Set[String] = map().keySet()
}

object MonixMDCAdapter {
  def init(): Unit = {
    val field = classOf[MDC].getDeclaredField("mdcAdapter")
    field.setAccessible(true)
    field.set(null, new MonixMDCAdapter)
  }
}
