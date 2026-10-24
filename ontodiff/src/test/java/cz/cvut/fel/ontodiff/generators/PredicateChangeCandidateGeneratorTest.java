package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.Engine;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.OWLDiffOntologyDiffService;
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

public class PredicateChangeCandidateGeneratorTest {

    private static final String NS = "http://example.org/";
    private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();
    private final PredicateChangeCandidateGenerator generator =
            new PredicateChangeCandidateGenerator();

    @Test
    public void usesWeakInterpretationConfidenceForUniquePair() throws Exception {
        DiffResult diff = diff(
                Set.of(edge("oldProperty")),
                Set.of(edge("newProperty")));

        ChangeCandidate candidate = generator.generate(diff).toList().get(0);

        assertEquals(CandidateConfidence.WEAK_INTERPRETATION,
                candidate.confidence(), 0.0);
    }

    @Test
    public void reducesConfidenceForAmbiguousPredicatePairings() throws Exception {
        DiffResult diff = diff(
                Set.of(edge("oldProperty1"), edge("oldProperty2")),
                Set.of(edge("newProperty1"), edge("newProperty2")));

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(4, candidates.size());
        for (ChangeCandidate candidate : candidates) {
            assertEquals(CandidateConfidence.WEAK_INTERPRETATION / 4.0,
                    candidate.confidence(), 0.0);
        }
    }

    private OWLAxiom edge(String property) {
        return dataFactory.getOWLSubClassOfAxiom(
                dataFactory.getOWLClass(IRI.create(NS + "Subject")),
                dataFactory.getOWLObjectSomeValuesFrom(
                        dataFactory.getOWLObjectProperty(IRI.create(NS + property)),
                        dataFactory.getOWLClass(IRI.create(NS + "Object"))));
    }

    private DiffResult diff(Set<OWLAxiom> removed,
                            Set<OWLAxiom> added) throws Exception {
        OWLOntologyManager oldManager = OWLManager.createOWLOntologyManager();
        OWLOntologyManager newManager = OWLManager.createOWLOntologyManager();
        OWLOntology original = oldManager.createOntology();
        OWLOntology update = newManager.createOntology();
        oldManager.addAxioms(original, removed);
        newManager.addAxioms(update, added);

        return new OWLDiffOntologyDiffService()
                .diff(original, update, Engine.SYNTACTIC);
    }
}
