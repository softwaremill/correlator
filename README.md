# Correlation id support

See [the blog](https://blog.softwaremill.com/correlation-ids-in-scala-using-monix-3aa11783db81) for introduction.

Currently supports [monix](https://monix.io), [http4s](https://http4s.org) & [logback](https://logback.qos.ch).

Usage:

* add the dependency: `"com.softwaremill.correlator" %% "monix-logback-http4s" % "0.1.1"` to your project
* create an object extending the `CorrelationId` class, e.g. `object MyCorrelationId extends CorrelationId()`
* call `MyCorrelationId.init()` immediately after your program starts (in the `main()` method)
* wrap your `HttpRoutes[Task]` with `MyCorrelationId.setCorrelationIdMiddleware`, so that a correlation id is
extracted from requests, or a new one is created.
* you can access the current correlation id (if any is set) using `MyCorrelationId.apply()`. 
