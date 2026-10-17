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
import org.semanticweb.owlapi.model.OWLAnnotation;
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

public class SynonymCandidateGeneratorTest {

    private static final IRI CLASS_IRI = IRI.create("http://example.org/Flu");
    private static final String OBO_IN_OWL = "http://www.geneontology.org/formats/oboInOwl#";
    private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();
    private final SynonymCandidateGenerator generator = new SynonymCandidateGenerator();

    @Test
    public void addsSynonymsForAllSupportedProperties() throws Exception {
        for (String property : List.of(
                "hasExactSynonym", "hasRelatedSynonym",
                "hasBroadSynonym", "hasNarrowSynonym")) {
            OWLAnnotationAssertionAxiom axiom = synonym(property, "flu", "en");
            DiffResult diff = diff(List.of(), List.of(axiom));
            List<ChangeCandidate> candidates = generator.generate(diff).toList();

            assertEquals(1, candidates.size());
            assertEquals(new HighLevelDiffService.NewSynonym(
                    CLASS_IRI.toString(), OBO_IN_OWL + property, "flu@en"),
                    candidates.get(0).change());
            assertEquals(Set.of(axiom), candidates.get(0).addedEvidence());
            assertTrue(FallbackChanges.create(diff,
                    new StateSpaceSearch().search(diff, candidates)).isEmpty());
        }
    }

    @Test
    public void removesSynonym() throws Exception {
        OWLAnnotationAssertionAxiom axiom = synonym("hasRelatedSynonym", "grippe", "en");
        DiffResult diff = diff(List.of(axiom), List.of());
        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(1, candidates.size());
        assertEquals(new HighLevelDiffService.RemoveSynonym(
                CLASS_IRI.toString(), OBO_IN_OWL + "hasRelatedSynonym", "grippe@en"),
                candidates.get(0).change());
        assertEquals(Set.of(axiom), candidates.get(0).removedEvidence());
        assertTrue(FallbackChanges.create(diff,
                new StateSpaceSearch().search(diff, candidates)).isEmpty());
    }

    @Test
    public void replacementBeatsSeparateAdditionAndRemoval() throws Exception {
        OWLAnnotationAssertionAxiom oldAxiom = synonym("hasExactSynonym", "flu", "en");
        OWLAnnotationAssertionAxiom newAxiom = synonym("hasExactSynonym", "influenza", "en");
        DiffResult diff = diff(List.of(oldAxiom), List.of(newAxiom));
        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(3, candidates.size());
        SearchState result = new StateSpaceSearch().search(diff, candidates);
        assertEquals(List.of(new HighLevelDiffService.SynonymReplacement(
                CLASS_IRI.toString(), OBO_IN_OWL + "hasExactSynonym",
                "flu@en", "influenza@en")),
                result.selectedCandidates().stream().map(ChangeCandidate::change).toList());
        assertEquals(1, result.cost());
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    @Test
    public void differentSynonymPropertiesStaySeparate() throws Exception {
        DiffResult diff = diff(
                List.of(synonym("hasExactSynonym", "flu", "en")),
                List.of(synonym("hasRelatedSynonym", "influenza", "en")));
        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().noneMatch(candidate ->
                candidate.change() instanceof HighLevelDiffService.SynonymReplacement));
        assertEquals(2, new StateSpaceSearch().search(diff, candidates).cost());
    }

    @Test
    public void ambiguousSynonymsStaySeparate() throws Exception {
        DiffResult diff = diff(
                List.of(
                        synonym("hasExactSynonym", "flu", "en"),
                        synonym("hasExactSynonym", "grippe", "en")),
                List.of(synonym("hasExactSynonym", "influenza", "en")));
        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(3, candidates.size());
        assertTrue(candidates.stream().noneMatch(candidate ->
                candidate.change() instanceof HighLevelDiffService.SynonymReplacement));
        SearchState result = new StateSpaceSearch().search(diff, candidates);
        assertEquals(3, result.cost());
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    @Test
    public void differentLanguagesStaySeparate() throws Exception {
        DiffResult diff = diff(
                List.of(synonym("hasExactSynonym", "flu", "en")),
                List.of(synonym("hasExactSynonym", "grippe", "fr")));

        assertEquals(2, generator.generate(diff).count());
    }

    @Test
    public void deletedClassDoesNotProduceReplacement() throws Exception {
        DiffResult diff = diff(
                List.of(synonym("hasExactSynonym", "flu", "en")),
                List.of(synonym("hasExactSynonym", "influenza", "en")),
                true,
                false);
        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().noneMatch(candidate ->
                candidate.change() instanceof HighLevelDiffService.SynonymReplacement));
        List<ChangeCandidate> allCandidates = Stream.concat(
                new NodeDeletionCandidateGenerator().generate(diff),
                candidates.stream()).toList();
        SearchState result = new StateSpaceSearch().search(diff, allCandidates);
        assertTrue(result.selectedCandidates().stream().anyMatch(candidate ->
                candidate.change() instanceof HighLevelDiffService.NodeDeletion));
        assertTrue(result.selectedCandidates().stream().anyMatch(candidate ->
                candidate.change() instanceof HighLevelDiffService.NewSynonym));
        assertTrue(FallbackChanges.create(diff, result).isEmpty());
    }

    @Test
    public void axiomAnnotationChangeDoesNotCreateSynonymChange() throws Exception {
        OWLAnnotationAssertionAxiom base = synonym("hasExactSynonym", "flu", "en");
        OWLAnnotation oldMetadata = dataFactory.getOWLAnnotation(
                dataFactory.getRDFSComment(), dataFactory.getOWLLiteral("old source"));
        OWLAnnotation newMetadata = dataFactory.getOWLAnnotation(
                dataFactory.getRDFSComment(), dataFactory.getOWLLiteral("new source"));
        OWLAnnotationAssertionAxiom oldAxiom = (OWLAnnotationAssertionAxiom)
                base.getAnnotatedAxiom(Set.of(oldMetadata));
        OWLAnnotationAssertionAxiom newAxiom = (OWLAnnotationAssertionAxiom)
                base.getAnnotatedAxiom(Set.of(newMetadata));
        DiffResult diff = diff(List.of(oldAxiom), List.of(newAxiom));

        assertEquals(1, diff.getOnlyInOriginal().size());
        assertEquals(1, diff.getOnlyInUpdate().size());
        assertTrue(generator.generate(diff).toList().isEmpty());
        assertEquals(2, FallbackChanges.create(diff,
                new StateSpaceSearch().search(diff, List.of())).size());
    }

    private OWLAnnotationAssertionAxiom synonym(String property, String text, String language) {
        return dataFactory.getOWLAnnotationAssertionAxiom(
                dataFactory.getOWLAnnotationProperty(IRI.create(OBO_IN_OWL + property)),
                CLASS_IRI,
                dataFactory.getOWLLiteral(text, language));
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
