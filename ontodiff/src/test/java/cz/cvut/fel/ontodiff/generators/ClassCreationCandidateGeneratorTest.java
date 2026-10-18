package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.Engine;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.changes.FallbackChanges;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import cz.cvut.fel.ontodiff.service.HighLevelDiffServiceImpl;
import cz.cvut.fel.ontodiff.service.OWLDiffOntologyDiffService;
import cz.cvut.fel.ontodiff.statesearch.SearchState;
import cz.cvut.fel.ontodiff.statesearch.StateSpaceSearch;
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
import static org.junit.Assert.assertTrue;

public class ClassCreationCandidateGeneratorTest {

    private static final IRI CLASS_IRI = IRI.create("http://example.org/Flu");
    private static final IRI PARENT_IRI = IRI.create("http://example.org/Disease");
    private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();
    private final ClassCreationCandidateGenerator generator = new ClassCreationCandidateGenerator();

    @Test
    public void newClassWithOwnedAxiomsProducesOneClassCreation() throws Exception {
        OWLAxiom parentDeclaration = dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLClass(PARENT_IRI));
        OWLAxiom classDeclaration = dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLClass(CLASS_IRI));
        OWLAxiom label = dataFactory.getOWLAnnotationAssertionAxiom(
                dataFactory.getRDFSLabel(), CLASS_IRI, dataFactory.getOWLLiteral("flu", "en"));
        OWLAxiom placement = dataFactory.getOWLSubClassOfAxiom(
                dataFactory.getOWLClass(CLASS_IRI), dataFactory.getOWLClass(PARENT_IRI));
        DiffResult diff = diff(Set.of(parentDeclaration),
                Set.of(parentDeclaration, classDeclaration, label, placement));

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(1, candidates.size());
        ChangeCandidate candidate = candidates.get(0);
        assertEquals(new HighLevelDiffService.ClassCreation(CLASS_IRI.toString()), candidate.change());
        assertEquals(Set.of(), candidate.removedEvidence());
        assertEquals(Set.of(classDeclaration, label, placement), candidate.addedEvidence());
        assertEquals(1, candidate.cost());
        assertTrue(!new HighLevelDiffServiceImpl().computeNodeCreations(diff)
                .contains(new HighLevelDiffService.NodeCreation(CLASS_IRI.toString())));

        SearchState result = new StateSpaceSearch().search(diff, candidates);
        assertEquals(List.of(candidate), result.selectedCandidates());
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    @Test
    public void nonClassDeclarationDoesNotProduceClassCreation() throws Exception {
        OWLAxiom propertyDeclaration = dataFactory.getOWLDeclarationAxiom(
                dataFactory.getOWLObjectProperty(IRI.create("http://example.org/partOf")));
        DiffResult diff = diff(Set.of(), Set.of(propertyDeclaration));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    @Test
    public void classAlreadyInOriginalSignatureDoesNotProduceClassCreation() throws Exception {
        OWLAxiom existingUsage = dataFactory.getOWLSubClassOfAxiom(
                dataFactory.getOWLClass(CLASS_IRI), dataFactory.getOWLClass(PARENT_IRI));
        OWLAxiom newDeclaration = dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLClass(CLASS_IRI));
        DiffResult diff = diff(Set.of(existingUsage), Set.of(existingUsage, newDeclaration));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    private DiffResult diff(Set<OWLAxiom> oldAxioms, Set<OWLAxiom> newAxioms) throws Exception {
        OWLOntologyManager oldManager = OWLManager.createOWLOntologyManager();
        OWLOntologyManager newManager = OWLManager.createOWLOntologyManager();
        OWLOntology original = oldManager.createOntology();
        OWLOntology update = newManager.createOntology();
        oldManager.addAxioms(original, oldAxioms);
        newManager.addAxioms(update, newAxioms);
        return new OWLDiffOntologyDiffService().diff(original, update, Engine.SYNTACTIC);
    }
}
