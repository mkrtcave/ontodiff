package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.Engine;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.changes.FallbackChanges;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
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

public class NodeCreationCandidateGeneratorTest {

    private static final IRI IRI_N = IRI.create("http://example.org/partOf");
    private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();
    private final NodeCreationCandidateGenerator generator = new NodeCreationCandidateGenerator();

    @Test
    public void objectPropertyDeclarationAndLabelBecomeNodeCreation() throws Exception {
        OWLAxiom declaration = dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLObjectProperty(IRI_N));
        OWLAxiom label = dataFactory.getOWLAnnotationAssertionAxiom(
                dataFactory.getRDFSLabel(), IRI_N, dataFactory.getOWLLiteral("part of"));
        DiffResult diff = diff(Set.of(), Set.of(declaration, label));

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(1, candidates.size());
        ChangeCandidate candidate = candidates.get(0);
        assertEquals(new HighLevelDiffService.NodeCreation(IRI_N.toString()), candidate.change());
        assertEquals(Set.of(declaration, label), candidate.addedEvidence());
        assertEquals(Set.of(), candidate.removedEvidence());
        assertTrue(new ClassCreationCandidateGenerator().generate(diff).toList().isEmpty());

        SearchState result = new StateSpaceSearch().search(diff, candidates);
        assertEquals(List.of(candidate), result.selectedCandidates());
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    @Test
    public void namedIndividualDeclarationBecomesNodeCreation() throws Exception {
        OWLAxiom declaration = dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLNamedIndividual(IRI_N));
        DiffResult diff = diff(Set.of(), Set.of(declaration));

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(1, candidates.size());
        assertEquals(new HighLevelDiffService.NodeCreation(IRI_N.toString()), candidates.get(0).change());
        assertEquals(Set.of(declaration), candidates.get(0).addedEvidence());
    }

    @Test
    public void classDeclarationIsOnlyClassCreation() throws Exception {
        OWLAxiom declaration = dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLClass(IRI_N));
        DiffResult diff = diff(Set.of(), Set.of(declaration));

        assertTrue(generator.generate(diff).toList().isEmpty());
        assertEquals(new HighLevelDiffService.ClassCreation(IRI_N.toString()),
                new ClassCreationCandidateGenerator().generate(diff).toList().get(0).change());
    }

    @Test
    public void declarationForExistingIriIsNotNodeCreation() throws Exception {
        OWLAxiom usage = dataFactory.getOWLObjectPropertyDomainAxiom(
                dataFactory.getOWLObjectProperty(IRI_N),
                dataFactory.getOWLClass(IRI.create("http://example.org/Thing")));
        OWLAxiom declaration = dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLObjectProperty(IRI_N));
        DiffResult diff = diff(Set.of(usage), Set.of(usage, declaration));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    @Test
    public void punnedClassAndPropertyHaveSeparateNonOverlappingEvidence() throws Exception {
        OWLAxiom classDeclaration = dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLClass(IRI_N));
        OWLAxiom propertyDeclaration = dataFactory.getOWLDeclarationAxiom(dataFactory.getOWLObjectProperty(IRI_N));
        OWLAxiom label = dataFactory.getOWLAnnotationAssertionAxiom(
                dataFactory.getRDFSLabel(), IRI_N, dataFactory.getOWLLiteral("shared IRI"));
        DiffResult diff = diff(Set.of(), Set.of(classDeclaration, propertyDeclaration, label));

        ChangeCandidate nodeCreation = generator.generate(diff).toList().get(0);
        ChangeCandidate classCreation = new ClassCreationCandidateGenerator().generate(diff).toList().get(0);

        assertEquals(Set.of(propertyDeclaration), nodeCreation.addedEvidence());
        assertEquals(Set.of(classDeclaration, label), classCreation.addedEvidence());

        SearchState result = new StateSpaceSearch().search(diff, List.of(nodeCreation, classCreation));
        assertEquals(2, result.selectedCandidates().size());
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
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
