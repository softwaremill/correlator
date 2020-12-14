# Correlation id support

[![Build Status](https://travis-ci.org/softwaremill/correlator.svg?branch=master)](https://travis-ci.org/softwaremill/correlator)

See [the blog](https://blog.softwaremill.com/correlation-ids-in-scala-using-monix-3aa11783db81) for introduction.

Currently supports [monix](https://monix.io) & [logback](https://logback.qos.ch).

Generic usage:

* add the dependency: `"com.softwaremill.correlator" %% "monix-logback-http4s" % "0.1.9"` to your project
* create an object extending the `CorrelationIdDecorator` class, e.g. `object MyCorrelationId extends CorrelationIdDecorator()`
* call `MyCorrelationId.init()` immediately after your program starts (in the `main()` method)
* create an implicit instance of `CorrelationIdSource` for given `T` from which you want to extract correlation id
* wrap any `T => Task[R]` function with `MyCorrelationId.withCorrelationId`, so that a correlation id is
extracted from the argument (using the defined `CorrelationIdSource`), or a new one is created.
* you can access the current correlation id (if any is set) using `MyCorrelationId.apply()` or `MyCorrelationId.applySync()`. 


For [http4s](https://http4s.org) integration:

* add the dependency: `"com.softwaremill.correlator" %% "monix-logback-http4s" % "0.1.9"` to your project
* create an object extending the `CorrelationIdDecorator` class, e.g. `object MyCorrelationId extends CorrelationIdDecorator()`
* call `MyCorrelationId.init()` immediately after your program starts (in the `main()` method)
* wrap your `HttpRoutes[Task]` with `Http4sCorrelationMiddleware(MyCorrelationId).withCorrelationId`, so that a correlation id is
extracted from the request (using the provided header name), or a new one is created.
* you can access the current correlation id (if any is set) using `MyCorrelationId.apply()` or `MyCorrelationId.applySync()`.

Logging each request with corresponding correlationId can be done in following way:
```scala
def loggingMiddleware[T, R](
                           service: HttpRoutes[Task],
                           logStartRequest: Request[Task] => Task[Unit] = req =>
                             Task(MyLogger.debug(s"Starting request to: ${req.uri.path}"))
                         ): HttpRoutes[Task] = Kleisli{ req: Request[Task] =>
    val setupAndService = for {
      _ <- logStartRequest(req)
      r <- service(req).value
    } yield r
    OptionT(setupAndService)
}

val middleware = Http4sCorrelationMiddleware(correlationIdDecorator)
middleware.withCorrelationId(loggingMiddleware(service))
```
