package be.ugent.solidlab.benchmark;

import be.solid.sdx.demo.queries.GetContactBasicQuery;
import be.ugent.solidlab.sdx.client.lib.SolidClient;
import be.ugent.solidlab.sdx.client.lib.backends.ldp.SolidLDPBackend;
import be.ugent.solidlab.sdx.client.lib.backends.ldp.SolidLDPContext;
import be.ugent.solidlab.sdx.client.lib.backends.ldp.StaticTargetResolver;
import com.apollographql.apollo3.api.ApolloResponse;
import com.apollographql.apollo3.rx2.Rx2Apollo;
import io.reactivex.Single;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.http.HttpHeaders;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParserBuilder;
import org.openjdk.jmh.annotations.*;

import java.io.StringReader;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class Benchie {
    private SolidClient<SolidLDPContext> client = new SolidClient<>( new SolidLDPBackend(null, "schema.graphqls"));
    private SolidLDPContext context = new SolidLDPContext( new StaticTargetResolver("http://localhost:3000/contacts/"));
    private String url = "http://localhost:3000/contacts/contacts.ttl";
    private GetContactBasicQuery query = new GetContactBasicQuery("http://localhost:3000/contacts/contacts.ttl#jdoe");
    private WebClient webClient = WebClient.create(Vertx.vertx());
    private String CONTENT_TYPE_TURTLE = "text/turtle";

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public GetContactBasicQuery.Contact sdxTest()  {
        Single<ApolloResponse<GetContactBasicQuery.Data>> queryResponse = Rx2Apollo.single(client.query(query, context));
        return  queryResponse.blockingGet().data.getContact();
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public Triple baseline() throws ExecutionException, InterruptedException {
        return baseLineInvocation();
    }

    private Triple baseLineInvocation() throws ExecutionException, InterruptedException {
        HttpResponse<io.vertx.core.buffer.Buffer> resp = webClient.getAbs(url).putHeader(HttpHeaders.ACCEPT, CONTENT_TYPE_TURTLE).send()
                .toCompletionStage().toCompletableFuture().get();
        String document = resp.bodyAsString();
        Graph graph = RDFParserBuilder.create().source( new StringReader(document)).lang(Lang.TURTLE).base(url).build().toGraph();
        Triple triple = graph.find(NodeFactory.createURI("http://localhost:3000/contacts/contacts.ttl#jdoe"), Node.ANY, Node.ANY).next();
        return triple;
    }


}
