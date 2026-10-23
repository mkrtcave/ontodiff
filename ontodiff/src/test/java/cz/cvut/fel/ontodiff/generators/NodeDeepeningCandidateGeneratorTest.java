package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.Engine;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import cz.cvut.fel.ontodiff.service.OWLDiffOntologyDiffService;
import cz.cvut.fel.ontodiff.statesearch.SearchState;
import cz.cvut.fel.ontodiff.statesearch.StateSpaceSearch;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NodeDeepeningCandidateGeneratorTest {

    private static final String NS = "http://example.org/deepening#";
    private static final IRI CHILD = IRI.create(NS + "Child");
    private static final IRI OLD_PARENT = IRI.create(NS + "OldParent");
    private static final IRI MIDDLE_PARENT = IRI.create(NS + "MiddleParent");
    private static final IRI NEW_PARENT = IRI.create(NS + "NewParent");
    private static final IRI UNRELATED_PARENT = IRI.create(NS + "UnrelatedParent");
    private static final IRI MOVED_RELATION = IRI.create(NS + "movedRelation");
    private static final IRI ANCESTOR_RELATION = IRI.create(NS + "ancestorRelation");
    private static final String SUBCLASS_OF =
            "http://www.w3.org/2000/01/rdf-schema#subClassOf";

    private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();
    private final NodeDeepeningCandidateGenerator generator =
            new NodeDeepeningCandidateGenerator();

    @Test
    public void recognizesMoveToDirectDescendant() throws Exception {
        OWLAxiom hierarchy = subclass(NEW_PARENT, OLD_PARENT);
        DiffResult diff = moveDiff(OLD_PARENT, NEW_PARENT, Set.of(hierarchy));

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(1, candidates.size());
        ChangeCandidate candidate = candidates.get(0);
        assertEquals(new HighLevelDiffService.NodeDeepening(
                OLD_PARENT.toString(),
                NEW_PARENT.toString(),
                new HighLevelDiffService.Edge(
                        CHILD.toString(),
                        SUBCLASS_OF,
                        OLD_PARENT.toString(),
                        "iri",
                        "iri",
                        "iri"),
                "iri",
                "iri"),
                candidate.change());
        assertEquals(1, candidate.removedEvidence().size());
        assertEquals(1, candidate.addedEvidence().size());
        assertEquals(CandidateConfidence.UNAMBIGUOUS_PAIR,
                candidate.confidence(), 0.0);
        assertEquals("new-object-is-proper-descendant-of-old-object",
                candidate.rule());
    }

    @Test
    public void followsTransitiveSubclassPath() throws Exception {
        DiffResult diff = moveDiff(
                OLD_PARENT,
                NEW_PARENT,
                Set.of(
                        subclass(NEW_PARENT, MIDDLE_PARENT),
                        subclass(MIDDLE_PARENT, OLD_PARENT)));

        assertEquals(1, generator.generate(diff).count());
    }

    @Test
    public void followsAnyPredicateInRelationalGraph() throws Exception {
        OWLAxiom hierarchy = edge(
                NEW_PARENT, ANCESTOR_RELATION, OLD_PARENT);
        OWLAxiom removed = edge(CHILD, MOVED_RELATION, OLD_PARENT);
        OWLAxiom added = edge(CHILD, MOVED_RELATION, NEW_PARENT);
        DiffResult diff = diff(
                Set.of(hierarchy, removed),
                Set.of(hierarchy, added));

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(1, candidates.size());
        HighLevelDiffService.NodeDeepening change =
                (HighLevelDiffService.NodeDeepening)
                        candidates.get(0).change();
        assertEquals(MOVED_RELATION.toString(),
                change.aboutEdge().predicate());
        assertEquals(OLD_PARENT.toString(), change.oldValue());
        assertEquals(NEW_PARENT.toString(), change.newValue());
    }

    @Test
    public void searchPrefersDeepeningOverGenericMove() throws Exception {
        DiffResult diff = moveDiff(
                OLD_PARENT,
                NEW_PARENT,
                Set.of(subclass(NEW_PARENT, OLD_PARENT)));
        List<ChangeCandidate> candidates = Stream.concat(
                new NodeMoveCandidateGenerator().generate(diff),
                generator.generate(diff)).toList();

        SearchState result = new StateSpaceSearch().search(diff, candidates);

        assertEquals(1, result.selectedCandidates().size());
        assertTrue(result.selectedCandidates().get(0).change()
                instanceof HighLevelDiffService.NodeDeepening);
    }

    @Test
    public void unrelatedMoveIsNotDeepening() throws Exception {
        DiffResult diff = moveDiff(
                OLD_PARENT,
                UNRELATED_PARENT,
                Set.of());

        assertTrue(generator.generate(diff).toList().isEmpty());
        assertEquals(1, new NodeMoveCandidateGenerator().generate(diff).count());
    }

    @Test
    public void moveToAncestorIsShallowingNotDeepening() throws Exception {
        DiffResult diff = moveDiff(
                NEW_PARENT,
                OLD_PARENT,
                Set.of(subclass(NEW_PARENT, OLD_PARENT)));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    @Test
    public void subclassCycleIsNotProperDescendance() throws Exception {
        DiffResult diff = moveDiff(
                OLD_PARENT,
                NEW_PARENT,
                Set.of(
                        subclass(NEW_PARENT, OLD_PARENT),
                        subclass(OLD_PARENT, NEW_PARENT)));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    private DiffResult moveDiff(
            IRI oldParent,
            IRI newParent,
            Set<OWLAxiom> unchangedHierarchy
    ) throws Exception {
        Set<OWLAxiom> originalAxioms = new java.util.HashSet<>(unchangedHierarchy);
        originalAxioms.add(subclass(CHILD, oldParent));

        Set<OWLAxiom> updatedAxioms = new java.util.HashSet<>(unchangedHierarchy);
        updatedAxioms.add(subclass(CHILD, newParent));

        return diff(originalAxioms, updatedAxioms);
    }

    private OWLAxiom subclass(IRI child, IRI parent) {
        OWLClass childClass = dataFactory.getOWLClass(child);
        OWLClass parentClass = dataFactory.getOWLClass(parent);
        return dataFactory.getOWLSubClassOfAxiom(childClass, parentClass);
    }

    private OWLAxiom edge(IRI subject, IRI predicate, IRI object) {
        return dataFactory.getOWLSubClassOfAxiom(
                dataFactory.getOWLClass(subject),
                dataFactory.getOWLObjectSomeValuesFrom(
                        dataFactory.getOWLObjectProperty(predicate),
                        dataFactory.getOWLClass(object)));
    }

    private DiffResult diff(Set<OWLAxiom> oldAxioms, Set<OWLAxiom> newAxioms)
            throws Exception {
        OWLOntologyManager oldManager = OWLManager.createOWLOntologyManager();
        OWLOntologyManager newManager = OWLManager.createOWLOntologyManager();
        OWLOntology original = oldManager.createOntology();
        OWLOntology update = newManager.createOntology();
        oldManager.addAxioms(original, oldAxioms);
        newManager.addAxioms(update, newAxioms);

        return new OWLDiffOntologyDiffService().diff(
                original, update, Engine.SYNTACTIC);
    }
}
