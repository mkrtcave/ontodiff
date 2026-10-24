package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.Engine;
import cz.cvut.fel.ontodiff.statesearch.SearchState;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import cz.cvut.fel.ontodiff.service.OWLDiffOntologyDiffService;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class NodeMoveCandidateGeneratorTest {

    private static final String BASE = "/ontologies/node-move/";
    private static final String NS = "http://example.org/disease-ontology#";

    private final NodeMoveCandidateGenerator generator =
            new NodeMoveCandidateGenerator();

    @Test
    public void detectsAllMovesBetweenTwoOntologyVersions() throws Exception {
        DiffResult diff = diffFrom(
                BASE + "node-move-v1.ttl",
                BASE + "node-move-v2.ttl"
        );

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(3, diff.getOnlyInOriginal().size());
        assertEquals(3, diff.getOnlyInUpdate().size());
        assertEquals(3, candidates.size());

        Map<String, HighLevelDiffService.NodeMove> movesByChild = candidates.stream()
                .map(candidate -> (HighLevelDiffService.NodeMove) candidate.change())
                .collect(Collectors.toMap(
                        HighLevelDiffService.NodeMove::chldIri,
                        move -> move
                ));

        assertMove(movesByChild, "Influenza", "InfectiousDisease", "RespiratoryDisease");
        assertMove(movesByChild, "CommonCold", "InfectiousDisease", "RespiratoryDisease");
        assertMove(movesByChild, "Pneumonia", "RespiratoryDisease", "InfectiousDisease");

        for (ChangeCandidate candidate : candidates) {
            assertEquals(1, candidate.removedEvidence().size());
            assertEquals(1, candidate.addedEvidence().size());
            assertEquals(CandidateConfidence.STRUCTURAL_INFERENCE,
                    candidate.confidence(), 0.0);
            assertEquals(1, candidate.cost());
            assertEquals("same-child-different-parent", candidate.rule());
        }

        SearchState state = SearchState.initial(diff);
        for (ChangeCandidate candidate : candidates) {
            assertTrue(state.canApply(candidate));
            state = state.apply(candidate);
        }

        assertTrue(state.isGoal());
        assertEquals(3, state.selectedCandidates().size());
        assertEquals(3, state.cost());
    }

    @Test
    public void reducesConfidenceForAmbiguousParentPairings() throws Exception {
        OWLOntology original = ontologyWithParents("OldParent1", "OldParent2");
        OWLOntology update = ontologyWithParents("NewParent1", "NewParent2");
        DiffResult diff = new OWLDiffOntologyDiffService()
                .diff(original, update, Engine.SYNTACTIC);

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(4, candidates.size());
        for (ChangeCandidate candidate : candidates) {
            assertEquals(CandidateConfidence.STRUCTURAL_INFERENCE / 4.0,
                    candidate.confidence(), 0.0);
        }
    }

    private void assertMove(
            Map<String, HighLevelDiffService.NodeMove> movesByChild,
            String child,
            String oldParent,
            String newParent) {
        HighLevelDiffService.NodeMove move = movesByChild.get(NS + child);

        assertNotNull("Missing move candidate for " + child, move);
        assertEquals(NS + oldParent, move.oldPrtIri());
        assertEquals(NS + newParent, move.newPrtIri());
    }

    private DiffResult diffFrom(String oldResource, String newResource) throws Exception {
        OWLOntology original = load(oldResource);
        OWLOntology update = load(newResource);

        return new OWLDiffOntologyDiffService()
                .diff(original, update, Engine.SYNTACTIC);
    }

    private OWLOntology load(String resource) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing test resource: " + resource);
            }
            return manager.loadOntologyFromOntologyDocument(input);
        }
    }

    private OWLOntology ontologyWithParents(String... parents) throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.createOntology();
        OWLDataFactory dataFactory = manager.getOWLDataFactory();

        for (String parent : parents) {
            manager.addAxiom(ontology, dataFactory.getOWLSubClassOfAxiom(
                    dataFactory.getOWLClass(IRI.create(NS + "Flu")),
                    dataFactory.getOWLClass(IRI.create(NS + parent))));
        }

        return ontology;
    }
}
