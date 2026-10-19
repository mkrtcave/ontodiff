package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.Engine;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.changes.FallbackChange;
import cz.cvut.fel.ontodiff.changes.FallbackChanges;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import cz.cvut.fel.ontodiff.service.OWLDiffOntologyDiffService;
import cz.cvut.fel.ontodiff.statesearch.SearchState;
import cz.cvut.fel.ontodiff.statesearch.StateSpaceSearch;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EquivalentClassesChangeCandidateGeneratorTest {

    private static final String OBO = "http://purl.obolibrary.org/obo/";
    private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();
    private final OWLClass definedClass = owlClass("APOLLO_SV_00000238");

    private final EquivalentClassesChangeCandidateGenerator generator = new EquivalentClassesChangeCandidateGenerator();

    @Test
    public void recognizesChangedAnonymousDefinitionForSameClass() throws Exception {
        OWLClassExpression oldExpression = definition("GO_0000003");
        OWLClassExpression newExpression = definition("APOLLO_SV_00000651");
        OWLEquivalentClassesAxiom oldAxiom = equivalent(definedClass, oldExpression);
        OWLEquivalentClassesAxiom newAxiom = equivalent(definedClass, newExpression);
        DiffResult diff = diff(List.of(oldAxiom), List.of(newAxiom));

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(1, candidates.size());
        ChangeCandidate candidate = candidates.get(0);
        assertEquals(new HighLevelDiffService.EquivalentClassesChange(
                definedClass.getIRI().toString(), oldExpression, newExpression),
                candidate.change());
        assertEquals(Set.of(oldAxiom), candidate.removedEvidence());
        assertEquals(Set.of(newAxiom), candidate.addedEvidence());
        assertEquals(CandidateConfidence.UNAMBIGUOUS_PAIR,
                candidate.confidence(), 0.0);
        assertEquals(1, candidate.cost());

        SearchState result = new StateSpaceSearch().search(diff, candidates);
        assertEquals(List.of(candidate), result.selectedCandidates());
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    @Test
    public void singleSidedEquivalenceRemainsAxiomFallback() throws Exception {
        OWLEquivalentClassesAxiom axiom = equivalent(
                definedClass, definition("GO_0000003"));
        DiffResult addition = diff(List.of(), List.of(axiom));
        SearchState addedState = new StateSpaceSearch().search(addition,
                generator.generate(addition).toList());
        assertEquals(new FallbackChange.AxiomAddition(axiom),
                FallbackChanges.create(addition, addedState).get(0).change());

        DiffResult removal = diff(List.of(axiom), List.of());
        SearchState removedState = new StateSpaceSearch().search(removal,
                generator.generate(removal).toList());
        assertEquals(new FallbackChange.AxiomRemoval(axiom),
                FallbackChanges.create(removal, removedState).get(0).change());
    }

    @Test
    public void differentNamedClassesAreNotPaired() throws Exception {
        DiffResult diff = diff(
                List.of(equivalent(definedClass, definition("GO_0000003"))),
                List.of(equivalent(owlClass("APOLLO_SV_99999999"),
                        definition("APOLLO_SV_00000651"))));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    @Test
    public void ambiguousDefinitionsAreNotPairedArbitrarily() throws Exception {
        DiffResult diff = diff(
                List.of(
                        equivalent(definedClass, definition("GO_0000003")),
                        equivalent(definedClass, definition("GO_0000004"))),
                List.of(equivalent(definedClass, definition("APOLLO_SV_00000651"))));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    @Test
    public void equivalenceBetweenNamedClassesRemainsAxiomFallback() throws Exception {
        DiffResult diff = diff(
                List.of(equivalent(definedClass, owlClass("GO_0000003"))),
                List.of(equivalent(definedClass, owlClass("APOLLO_SV_00000651"))));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    private OWLClass owlClass(String localId) {
        return dataFactory.getOWLClass(IRI.create(OBO + localId));
    }

    private OWLClassExpression definition(String fillerId) {
        return dataFactory.getOWLObjectIntersectionOf(owlClass("OBI_0100026"),
                dataFactory.getOWLObjectSomeValuesFrom(
                        dataFactory.getOWLObjectProperty(IRI.create(OBO + "BFO_0000067")),
                        owlClass(fillerId)));
    }

    private OWLEquivalentClassesAxiom equivalent(OWLClass namedClass, OWLClassExpression expression) {
        return dataFactory.getOWLEquivalentClassesAxiom(namedClass, expression);
    }

    private DiffResult diff(List<OWLEquivalentClassesAxiom> removed, List<OWLEquivalentClassesAxiom> added)
            throws OWLOntologyCreationException {
        OWLOntologyManager oldManager = OWLManager.createOWLOntologyManager();
        OWLOntologyManager newManager = OWLManager.createOWLOntologyManager();
        OWLOntology original = oldManager.createOntology();
        OWLOntology update = newManager.createOntology();
        removed.forEach(axiom -> oldManager.addAxiom(original, axiom));
        added.forEach(axiom -> newManager.addAxiom(update, axiom));

        return new OWLDiffOntologyDiffService().diff(original, update, Engine.SYNTACTIC);
    }
}
