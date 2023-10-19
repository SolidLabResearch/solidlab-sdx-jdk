package be.ugent.solidlab.sdx.client.lib.backends.ldp

import be.ugent.solidlab.sdx.client.lib.backends.ldp.data.convertToRDF
import be.ugent.solidlab.sdx.client.lib.backends.ldp.data.encodeAsTurtle
import be.ugent.solidlab.sdx.client.lib.backends.ldp.data.testGraphs
import io.vertx.core.Handler
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import org.apache.http.HttpHeaders

class MockedLDPRequestHandler : Handler<HttpServerRequest> {
    override fun handle(req: HttpServerRequest) {
        when (req.method()) {
            HttpMethod.HEAD -> req.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/turtle")
                .putHeader("Link", "\t<http://www.w3.org/ns/ldp#Resource>; rel=\"type\"").end()

            HttpMethod.GET -> req.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/turtle")
                .putHeader("Link", "\t<http://www.w3.org/ns/ldp#Resource>; rel=\"type\"").end(
                    testGraphs[req.path()]?.let { convertToRDF(it).encodeAsTurtle() }
                        ?: throw RuntimeException("Path not found!"))

            HttpMethod.POST -> req.response().setStatusCode(201).end()
            HttpMethod.PATCH, HttpMethod.PUT, HttpMethod.DELETE -> req.response().setStatusCode(204).end()
            else -> req.response().setStatusCode(500).end("Unexpected request")
        }
    }
}
