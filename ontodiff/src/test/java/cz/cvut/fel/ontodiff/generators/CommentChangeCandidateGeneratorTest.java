package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.Engine;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.changes.FallbackChanges;
import cz.cvut.fel.ontodiff.changes.LiteralValue;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import cz.cvut.fel.ontodiff.service.OWLDiffOntologyDiffService;
import cz.cvut.fel.ontodiff.statesearch.SearchState;
import cz.cvut.fel.ontodiff.statesearch.StateSpaceSearch;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommentChangeCandidateGeneratorTest {

    private static final IRI CLASS_IRI = IRI.create("http://example.org/Flu");
    private static final IRI PROPERTY_IRI = IRI.create("http://example.org/hasAgent");
    private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();
    private final CommentChangeCandidateGenerator generator = new CommentChangeCandidateGenerator();
    private final CommentAdditionRemovalCandidateGenerator atomicGenerator =
            new CommentAdditionRemovalCandidateGenerator();

    @Test
    public void recognizesChangedCommentAsOneCompoundCandidate() throws Exception {
        OWLAnnotationAssertionAxiom oldComment = comment(CLASS_IRI, "A viral disease.", "en");
        OWLAnnotationAssertionAxiom newComment = comment(CLASS_IRI, "A respiratory viral disease.", "en");
        DiffResult diff = diff(List.of(oldComment), List.of(newComment));

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(1, candidates.size());
        ChangeCandidate candidate = candidates.get(0);
        assertEquals(new HighLevelDiffService.CommentChange(
                CLASS_IRI.toString(),
                literalValue("A viral disease.", "en"),
                literalValue("A respiratory viral disease.", "en")),
                candidate.change());
        assertEquals(Set.of(oldComment), candidate.removedEvidence());
        assertEquals(Set.of(newComment), candidate.addedEvidence());
        assertEquals(CandidateConfidence.UNAMBIGUOUS_PAIR,
                candidate.confidence(), 0.0);
        assertEquals(1, candidate.cost());

        SearchState result = new StateSpaceSearch().search(diff, candidates);
        assertEquals(List.of(candidate), result.selectedCandidates());
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    @Test
    public void alsoRecognizesCommentsOnProperties() throws Exception {
        DiffResult diff = diff(
                List.of(comment(PROPERTY_IRI, "Old comment", "en")),
                List.of(comment(PROPERTY_IRI, "New comment", "en")));

        ChangeCandidate candidate = generator.generate(diff).toList().get(0);
        assertEquals(new HighLevelDiffService.CommentChange(
                PROPERTY_IRI.toString(),
                literalValue("Old comment", "en"),
                literalValue("New comment", "en")),
                candidate.change());
    }

    @Test
    public void singleSidedCommentsBecomeDedicatedCandidates() throws Exception {
        OWLAnnotationAssertionAxiom addedComment = comment(CLASS_IRI, "New comment", "en");
        DiffResult addition = diff(List.of(), List.of(addedComment));
        assertTrue(generator.generate(addition).toList().isEmpty());
        List<ChangeCandidate> additionCandidates = atomicGenerator.generate(addition).toList();
        SearchState addedState = new StateSpaceSearch().search(addition,
                additionCandidates);
        assertEquals(List.of(new HighLevelDiffService.CommentAddition(
                CLASS_IRI.toString(), "New comment")),
                addedState.selectedCandidates().stream().map(ChangeCandidate::change).toList());
        assertTrue(FallbackChanges.create(addition, addedState).isEmpty());

        OWLAnnotationAssertionAxiom removedComment = comment(CLASS_IRI, "Old comment", "en");
        DiffResult removal = diff(List.of(removedComment), List.of());
        assertTrue(generator.generate(removal).toList().isEmpty());
        List<ChangeCandidate> removalCandidates = atomicGenerator.generate(removal).toList();
        SearchState removedState = new StateSpaceSearch().search(removal,
                removalCandidates);
        assertEquals(List.of(new HighLevelDiffService.CommentRemoval(
                CLASS_IRI.toString(), "Old comment")),
                removedState.selectedCandidates().stream().map(ChangeCandidate::change).toList());
        assertTrue(FallbackChanges.create(removal, removedState).isEmpty());
    }

    @Test
    public void pairedCommentsPreferOneCompoundChange() throws Exception {
        DiffResult diff = diff(
                List.of(comment(CLASS_IRI, "Old", "en")),
                List.of(comment(CLASS_IRI, "New", "en")));
        List<ChangeCandidate> candidates = Stream.concat(
                generator.generate(diff), atomicGenerator.generate(diff)).toList();

        assertEquals(3, candidates.size());
        SearchState result = new StateSpaceSearch().search(diff, candidates);
        assertEquals(List.of(new HighLevelDiffService.CommentChange(
                CLASS_IRI.toString(),
                literalValue("Old", "en"),
                literalValue("New", "en"))),
                result.selectedCandidates().stream().map(ChangeCandidate::change).toList());
        assertEquals(1, result.cost());
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    @Test
    public void differentLanguagesAreNotPaired() throws Exception {
        DiffResult diff = diff(
                List.of(comment(CLASS_IRI, "Old", "en")),
                List.of(comment(CLASS_IRI, "Nouveau", "fr")));

        assertTrue(generator.generate(diff).toList().isEmpty());
        List<ChangeCandidate> candidates = atomicGenerator.generate(diff).toList();
        assertEquals(2, candidates.size());
        SearchState result = new StateSpaceSearch().search(diff, candidates);
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    @Test
    public void ambiguousCommentsAreNotPairedArbitrarily() throws Exception {
        DiffResult diff = diff(
                List.of(
                        comment(CLASS_IRI, "Old one", "en"),
                        comment(CLASS_IRI, "Old two", "en")),
                List.of(comment(CLASS_IRI, "New", "en")));

        assertTrue(generator.generate(diff).toList().isEmpty());
        List<ChangeCandidate> candidates = atomicGenerator.generate(diff).toList();
        assertEquals(3, candidates.size());
        SearchState result = new StateSpaceSearch().search(diff, candidates);
        assertEquals(3, result.selectedCandidates().size());
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    @Test
    public void commentOnDeletedClassDoesNotCompeteWithNodeDeletion() throws Exception {
        DiffResult diff = diff(
                List.of(comment(CLASS_IRI, "Old", "en")),
                List.of(comment(CLASS_IRI, "New", "en")),
                true,
                false);

        assertTrue(generator.generate(diff).toList().isEmpty());
        List<ChangeCandidate> candidates = Stream.concat(
                new NodeDeletionCandidateGenerator().generate(diff),
                atomicGenerator.generate(diff)).toList();
        SearchState result = new StateSpaceSearch().search(diff, candidates);
        assertTrue(result.selectedCandidates().stream()
                .anyMatch(candidate -> candidate.change() instanceof HighLevelDiffService.NodeDeletion));
        assertTrue(result.selectedCandidates().stream()
                .anyMatch(candidate -> candidate.change() instanceof HighLevelDiffService.CommentAddition));
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    private OWLAnnotationAssertionAxiom comment(IRI subject, String text, String language) {
        return dataFactory.getOWLAnnotationAssertionAxiom(
                dataFactory.getRDFSComment(),
                subject,
                dataFactory.getOWLLiteral(text, language));
    }

    private LiteralValue literalValue(String text, String language) {
        return LiteralValue.from(dataFactory.getOWLLiteral(text, language));
    }

    private DiffResult diff(
            List<OWLAnnotationAssertionAxiom> removed,
            List<OWLAnnotationAssertionAxiom> added
    ) throws OWLOntologyCreationException {
        return diff(removed, added, false, false);
    }

    private DiffResult diff(
            List<OWLAnnotationAssertionAxiom> removed,
            List<OWLAnnotationAssertionAxiom> added,
            boolean declareOriginalClass,
            boolean declareUpdatedClass
    ) throws OWLOntologyCreationException {
        OWLOntologyManager oldManager = OWLManager.createOWLOntologyManager();
        OWLOntologyManager newManager = OWLManager.createOWLOntologyManager();
        OWLOntology original = oldManager.createOntology();
        OWLOntology update = newManager.createOntology();
        if (declareOriginalClass) {
            oldManager.addAxiom(original, dataFactory.getOWLDeclarationAxiom(
                    dataFactory.getOWLClass(CLASS_IRI)));
        }
        if (declareUpdatedClass) {
            newManager.addAxiom(update, dataFactory.getOWLDeclarationAxiom(
                    dataFactory.getOWLClass(CLASS_IRI)));
        }
        removed.forEach(axiom -> oldManager.addAxiom(original, axiom));
        added.forEach(axiom -> newManager.addAxiom(update, axiom));

        return new OWLDiffOntologyDiffService().diff(original, update, Engine.SYNTACTIC);
    }
}
