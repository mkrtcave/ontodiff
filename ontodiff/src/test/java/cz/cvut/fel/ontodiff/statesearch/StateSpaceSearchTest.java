package cz.cvut.fel.ontodiff.statesearch;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.Engine;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class StateSpaceSearchTest {

    private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();

    @Test
    public void higherConfidenceWinsWhenCostAndEvidenceAreEqual() throws Exception {
        OWLAxiom evidence = declaration("A");
        DiffResult diff = diff(evidence);
        ChangeCandidate lowerConfidence = candidate("lower", Set.of(evidence), 0.4, 1);
        ChangeCandidate higherConfidence = candidate("higher", Set.of(evidence), 0.9, 1);

        for (List<ChangeCandidate> ordering : List.of(List.of(lowerConfidence, higherConfidence),
                List.of(higherConfidence, lowerConfidence))) {
            SearchState result = new StateSpaceSearch().search(diff, ordering);

            assertEquals(List.of(higherConfidence), result.selectedCandidates());
            assertEquals(1, result.cost());
            assertEquals(-Math.log(0.9), result.confidencePenalty(), 1.0e-12);
        }
    }

    @Test
    public void lowerCostWinsBeforeHigherConfidence() throws Exception {
        OWLAxiom firstEvidence = declaration("A");
        OWLAxiom secondEvidence = declaration("B");
        DiffResult diff = diff(firstEvidence, secondEvidence);
        ChangeCandidate lowerCost = candidate("compound", Set.of(firstEvidence, secondEvidence), 0.2, 1);
        ChangeCandidate highConfidenceFirst = candidate("first", Set.of(firstEvidence), 1.0, 1);
        ChangeCandidate highConfidenceSecond = candidate("second", Set.of(secondEvidence), 1.0, 1);

        SearchState result = new StateSpaceSearch().search(diff, List.of(highConfidenceFirst, highConfidenceSecond, lowerCost));

        assertEquals(List.of(lowerCost), result.selectedCandidates());
        assertEquals(1, result.cost());
    }

    @Test
    public void rejectsConfidenceOutsideProbabilityInterval() {
        OWLAxiom evidence = declaration("A");

        assertThrows(IllegalArgumentException.class,
                () -> candidate("zero", Set.of(evidence), 0.0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> candidate("greater-than-one", Set.of(evidence), 1.1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> candidate("not-a-number", Set.of(evidence), Double.NaN, 1));
    }

    private ChangeCandidate candidate(String id, Set<OWLAxiom> evidence,
                                      double confidence, int cost) {
        return new ChangeCandidate(
                new HighLevelDiffService.NodeCreation("http://example.org/" + id),
                Set.of(),
                evidence,
                confidence,
                cost,
                "test-rule"
        );
    }

    private OWLAxiom declaration(String localName) {
        return dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLClass(IRI.create("http://example.org/" + localName)));
    }

    private DiffResult diff(OWLAxiom... addedAxioms) throws Exception {
        OWLOntologyManager oldManager = OWLManager.createOWLOntologyManager();
        OWLOntologyManager newManager = OWLManager.createOWLOntologyManager();
        OWLOntology original = oldManager.createOntology();
        OWLOntology update = newManager.createOntology();
        newManager.addAxioms(update, Set.of(addedAxioms));

        return DiffResult.builder()
                .original(original)
                .update(update)
                .engine(Engine.SYNTACTIC)
                .onlyInUpdate(List.of(addedAxioms))
                .build();
    }
}
