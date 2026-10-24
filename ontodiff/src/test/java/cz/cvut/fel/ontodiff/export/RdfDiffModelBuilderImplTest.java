package cz.cvut.fel.ontodiff.export;

import cz.cvut.fel.ontodiff.changes.LiteralValue;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Literal;
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

    @Test
    public void preservesLanguageAndDatatypeOfRenamedLabel() {
        String rdfLangString =
                "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString";
        HighLevelDiffService.NodeRename rename =
                new HighLevelDiffService.NodeRename(
                        "http://example.org/Flu",
                        new LiteralValue("Influenza", "en", rdfLangString),
                        new LiteralValue("Flu", "en", rdfLangString));

        Dataset dataset = new RdfDiffModelBuilderImpl().build(
                diffWithRename(rename),
                metadata(),
                GRAPH_IRI);
        Model model = dataset.getNamedModel(GRAPH_IRI);
        Resource change = model.createResource(RUN_IRI + "#change-NodeRename-1");
        Literal oldValue = change.getProperty(
                model.createProperty(KGCL_NS, "old_value")).getLiteral();
        Literal newValue = change.getProperty(
                model.createProperty(KGCL_NS, "new_value")).getLiteral();

        assertTrue(oldValue.getLexicalForm().equals("Influenza"));
        assertTrue(oldValue.getLanguage().equals("en"));
        assertTrue(oldValue.getDatatypeURI().equals(rdfLangString));
        assertTrue(newValue.getLexicalForm().equals("Flu"));
        assertTrue(newValue.getLanguage().equals("en"));
        assertTrue(newValue.getDatatypeURI().equals(rdfLangString));
    }

    @Test
    public void preservesDatatypeOfRenamedTypedLabel() {
        String xsdString = "http://www.w3.org/2001/XMLSchema#string";
        HighLevelDiffService.NodeRename rename =
                new HighLevelDiffService.NodeRename(
                        "http://example.org/Flu",
                        new LiteralValue("Influenza", "", xsdString),
                        new LiteralValue("Flu", "", xsdString));

        Dataset dataset = new RdfDiffModelBuilderImpl().build(
                diffWithRename(rename),
                metadata(),
                GRAPH_IRI);
        Model model = dataset.getNamedModel(GRAPH_IRI);
        Resource change = model.createResource(RUN_IRI + "#change-NodeRename-1");
        Literal oldValue = change.getProperty(
                model.createProperty(KGCL_NS, "old_value")).getLiteral();

        assertTrue(oldValue.getLanguage().isEmpty());
        assertTrue(oldValue.getDatatypeURI().equals(xsdString));
    }

    private RdfDiffModelBuilder.DiffRunMetadata metadata() {
        return new RdfDiffModelBuilder.DiffRunMetadata(
                RUN_IRI,
                "http://example.org/ontology/v1",
                "http://example.org/ontology/v2",
                null,
                "ontodiff",
                "1.0",
                Instant.parse("2026-10-19T12:00:00Z"));
    }

    private HighLevelDiffService.HighLevelDiff emptyDiff() {
        return new HighLevelDiffService.HighLevelDiff(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    private HighLevelDiffService.HighLevelDiff diffWithRename(
            HighLevelDiffService.NodeRename rename) {
        return new HighLevelDiffService.HighLevelDiff(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(rename), Set.of(), Set.of(),
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }
}
