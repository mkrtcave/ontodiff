package cz.cvut.fel.ontodiff.export;

import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RdfDiffModelBuilderImplTest {

    private static final String OND_NS = "https://w3id.org/ontodiff/schema#";
    private static final String KGCL_NS = "https://w3id.org/kgcl/";
    private static final String PROV_NS = "http://www.w3.org/ns/prov#";
    private static final String RUN_IRI = "http://example.org/diff/run-1";
    private static final String GRAPH_IRI = "http://example.org/diff/graph-1";

    @Test
    public void linksKgclTransactionThroughDiffResult() {
        RdfDiffModelBuilder.DiffRunMetadata metadata =
                new RdfDiffModelBuilder.DiffRunMetadata(
                        RUN_IRI,
                        "http://example.org/ontology/v1",
                        "http://example.org/ontology/v2",
                        null,
                        "ontodiff",
                        "1.0",
                        Instant.parse("2026-10-19T12:00:00Z"));

        Dataset dataset = new RdfDiffModelBuilderImpl().build(emptyDiff(), metadata, GRAPH_IRI);
        Model metadataModel = dataset.getDefaultModel();
        Model changeModel = dataset.getNamedModel(GRAPH_IRI);

        Resource diffRun = metadataModel.createResource(RUN_IRI);
        Resource diffResult = metadataModel.createResource(RUN_IRI + "#result");
        Resource transaction = metadataModel.createResource(RUN_IRI + "#tx");
        Property result = metadataModel.createProperty(OND_NS, "result");
        Property hasTransaction = metadataModel.createProperty(OND_NS, "hasKgclTransaction");
        Property generated = metadataModel.createProperty(PROV_NS, "generated");

        assertTrue(metadataModel.contains(diffRun, result, diffResult));
        assertTrue(metadataModel.contains(diffResult, RDF.type,
                metadataModel.createResource(OND_NS + "DiffResult")));
        assertTrue(metadataModel.contains(diffResult, hasTransaction, transaction));
        assertFalse(metadataModel.contains(diffRun, hasTransaction));
        assertTrue(metadataModel.contains(diffRun, generated, diffResult));
        assertFalse(metadataModel.contains(diffRun, generated, transaction));
        assertTrue(changeModel.contains(
                changeModel.createResource(RUN_IRI + "#tx"),
                RDF.type,
                changeModel.createResource(KGCL_NS + "Transaction")));
    }

    private HighLevelDiffService.HighLevelDiff emptyDiff() {
        return new HighLevelDiffService.HighLevelDiff(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }
}
