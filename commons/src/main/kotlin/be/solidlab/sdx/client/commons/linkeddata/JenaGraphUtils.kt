package be.solidlab.sdx.client.commons.linkeddata

import org.apache.jena.graph.Graph
import org.apache.jena.graph.Triple
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFParserBuilder
import org.apache.jena.sparql.graph.GraphFactory
import org.apache.jena.util.iterator.ExtendedIterator
import java.io.File
import java.io.StringReader
import java.io.StringWriter

fun Graph.add(other: Graph): Graph {
    other.find().asSequence().forEach { this.add(it) }
    return this
}

fun Graph.add(triples: ExtendedIterator<Triple>): Graph {
    triples.forEach { this.add(it) }
    return this
}

object GraphIO {
    fun from(file: File): Graph {
        val result = GraphFactory.createDefaultGraph()
        RDFDataMgr.read(result, file.toURI().toString())
        return result
    }

    fun from(stringRepresentation: String, lang: Lang = Lang.TURTLE, base: String? = null): Graph {
        val builder = RDFParserBuilder.create().source(StringReader(stringRepresentation)).lang(lang)
        if (base != null) {
            builder.base(base)
        }
        return builder.toGraph()
    }

    fun encodeAsString(graph: Graph, lang: Lang = Lang.TURTLE): String {
        return StringWriter().use {
            RDFDataMgr.write(it, graph, lang)
            it.toString()
        }
    }
}

