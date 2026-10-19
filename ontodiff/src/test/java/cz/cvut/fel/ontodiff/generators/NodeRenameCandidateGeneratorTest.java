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
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NodeRenameCandidateGeneratorTest {

    private static final IRI CLASS_IRI = IRI.create("http://example.org/Flu");
    private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();
    private final NodeRenameCandidateGenerator generator = new NodeRenameCandidateGenerator();

    @Test
    public void recognizesChangedLabelAsOneCompoundCandidate() throws Exception {
        OWLAnnotationAssertionAxiom oldLabel = label("Influenza", "en");
        OWLAnnotationAssertionAxiom newLabel = label("Flu", "en");
        DiffResult diff = diff(List.of(oldLabel), List.of(newLabel));

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(1, candidates.size());
        ChangeCandidate candidate = candidates.get(0);
        assertEquals(new HighLevelDiffService.NodeRename(
                CLASS_IRI.toString(), "Influenza", "Flu"), candidate.change());
        assertEquals(Set.of(oldLabel), candidate.removedEvidence());
        assertEquals(Set.of(newLabel), candidate.addedEvidence());
        assertEquals(CandidateConfidence.UNAMBIGUOUS_PAIR,
                candidate.confidence(), 0.0);
        assertEquals(1, candidate.cost());

        SearchState result = new StateSpaceSearch().search(diff, candidates);
        assertEquals(List.of(candidate), result.selectedCandidates());
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    @Test
    public void singleSidedLabelsRemainAnnotationFallbacks() throws Exception {
        OWLAnnotationAssertionAxiom addedLabel = label("Flu", "en");
        DiffResult addition = diff(List.of(), List.of(addedLabel));
        SearchState addedState = new StateSpaceSearch().search(addition,
                generator.generate(addition).toList());

        assertEquals(new FallbackChange.AnnotationAddition(addedLabel),
                FallbackChanges.create(addition, addedState).get(0).change());

        OWLAnnotationAssertionAxiom removedLabel = label("Influenza", "en");
        DiffResult removal = diff(List.of(removedLabel), List.of());
        SearchState removedState = new StateSpaceSearch().search(removal,
                generator.generate(removal).toList());

        assertEquals(new FallbackChange.AnnotationRemoval(removedLabel),
                FallbackChanges.create(removal, removedState).get(0).change());
    }

    @Test
    public void differentLanguagesAreNotPairedAsRename() throws Exception {
        DiffResult diff = diff(
                List.of(label("Influenza", "en")),
                List.of(label("Grippe", "fr")));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    @Test
    public void ambiguousLabelsAreNotPairedArbitrarily() throws Exception {
        DiffResult diff = diff(List.of(label("Flu", "en"), label("Influenza", "en")),
                List.of(label("Grip", "en"), label("Grippe", "en")));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    private OWLAnnotationAssertionAxiom label(String text, String language) {
        return dataFactory.getOWLAnnotationAssertionAxiom(
                dataFactory.getRDFSLabel(),
                CLASS_IRI,
                dataFactory.getOWLLiteral(text, language));
    }

    private DiffResult diff(List<OWLAnnotationAssertionAxiom> removed, List<OWLAnnotationAssertionAxiom> added)
            throws OWLOntologyCreationException {
        OWLOntologyManager oldManager = OWLManager.createOWLOntologyManager();
        OWLOntologyManager newManager = OWLManager.createOWLOntologyManager();
        OWLOntology original = oldManager.createOntology();
        OWLOntology update = newManager.createOntology();
        OWLAxiom declaration = dataFactory.getOWLDeclarationAxiom(
                dataFactory.getOWLClass(CLASS_IRI));

        oldManager.addAxiom(original, declaration);
        newManager.addAxiom(update, declaration);
        removed.forEach(axiom -> oldManager.addAxiom(original, axiom));
        added.forEach(axiom -> newManager.addAxiom(update, axiom));

        return new OWLDiffOntologyDiffService().diff(original, update, Engine.SYNTACTIC);
    }
}
