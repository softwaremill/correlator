package com.softwaremill.correlator

import com.softwaremill.correlator.CorrelationId.CorrelationIdAware
import monix.eval.Task
import org.http4s.Request
import org.http4s.util.CaseInsensitiveString

object Http4s {
  val HeaderName: String = "X-Correlation-ID"

  implicit val awareness: CorrelationIdAware[Request[Task]] = (t: Request[Task]) => {
    t.headers.get(CaseInsensitiveString(HeaderName)).map(_.value)
  }
}
