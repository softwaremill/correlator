package com.softwaremill.correlator

import cats.data.{Kleisli, OptionT}
import com.softwaremill.correlator.CorrelationId.CorrelationIdAware
import monix.eval.Task
import org.http4s.Request
import org.http4s.util.CaseInsensitiveString

class Http4sCorrelationWrapper(correlationId: CorrelationId) {

  def setCorrelationIdMiddleware[T, R](
      service: Kleisli[OptionT[Task, *], T, R]
  )(implicit awareness: CorrelationIdAware[T]): Kleisli[OptionT[Task, *], T, R] = {
    val runOptionT: Kleisli[Task, T, Option[R]] = Kleisli(service.run.andThen(_.value))
    correlationId.setCorrelationIdMiddleware[T, Option[R]](runOptionT).mapF(OptionT.apply)
  }
}

object Http4sCorrelationWrapper {
  def apply(correlationId: CorrelationId): Http4sCorrelationWrapper = new Http4sCorrelationWrapper(correlationId)

  val HeaderName: String = "X-Correlation-ID"

  implicit val awareness: CorrelationIdAware[Request[Task]] = (t: Request[Task]) => {
    t.headers.get(CaseInsensitiveString(HeaderName)).map(_.value)
  }
}
