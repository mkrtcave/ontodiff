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
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IriRenameCandidateGeneratorTest {

    private static final String NS = "http://example.org/iri-rename#";
    private static final IRI OLD_IRI = IRI.create(NS + "OldIdentifier");
    private static final IRI NEW_IRI = IRI.create(NS + "NewIdentifier");
    private static final IRI CHILD_IRI = IRI.create(NS + "Child");
    private static final IRI DOMAIN_IRI = IRI.create(NS + "Domain");

    private final OWLDataFactory dataFactory = OWLManager.getOWLDataFactory();
    private final IriRenameCandidateGenerator generator =
            new IriRenameCandidateGenerator();

    @Test
    public void recognizesClassIriRenameAcrossCompleteAxiomNeighbourhood() throws Exception {
        OWLAxiom oldDeclaration = classDeclaration(OLD_IRI);
        OWLAxiom oldLabel = label(OLD_IRI, "Influenza");
        OWLAxiom oldIncomingEdge = subclass(CHILD_IRI, OLD_IRI);
        OWLAxiom newDeclaration = classDeclaration(NEW_IRI);
        OWLAxiom newLabel = label(NEW_IRI, "Influenza");
        OWLAxiom newIncomingEdge = subclass(CHILD_IRI, NEW_IRI);

        DiffResult diff = diff(Set.of(oldDeclaration, oldLabel, oldIncomingEdge),
                            Set.of(newDeclaration, newLabel, newIncomingEdge));

        List<ChangeCandidate> candidates = generator.generate(diff).toList();

        assertEquals(1, candidates.size());
        ChangeCandidate candidate = candidates.get(0);
        assertEquals(new HighLevelDiffService.IriRename(OLD_IRI.toString(), NEW_IRI.toString()), candidate.change());
        assertEquals(Set.of(oldDeclaration, oldLabel, oldIncomingEdge), candidate.removedEvidence());
        assertEquals(Set.of(newDeclaration, newLabel, newIncomingEdge), candidate.addedEvidence());
        assertEquals(CandidateConfidence.UNAMBIGUOUS_PAIR, candidate.confidence(), 0.0);
        assertEquals(1, candidate.cost());
        assertEquals(
                "exact-full-iri-substitution-with-matching-axiom-neighbourhood",
                candidate.rule());

        List<ChangeCandidate> competingCandidates = Stream.of(
                        generator.generate(diff),
                        new NodeDeletionCandidateGenerator().generate(diff),
                        new ClassCreationCandidateGenerator().generate(diff),
                        new NodeMoveCandidateGenerator().generate(diff))
                .flatMap(stream -> stream)
                .toList();

        SearchState result = new StateSpaceSearch().search(
                diff, competingCandidates);
        assertEquals(List.of(candidate), result.selectedCandidates());
    }

    @Test
    public void recognizesObjectPropertyIriRename() throws Exception {
        DiffResult diff = diff(Set.of(objectPropertyDeclaration(OLD_IRI), objectPropertyDomain(OLD_IRI, DOMAIN_IRI)),
                            Set.of(objectPropertyDeclaration(NEW_IRI), objectPropertyDomain(NEW_IRI, DOMAIN_IRI)));

        assertEquals(1, generator.generate(diff).count());
    }

    @Test
    public void recognizesIriInAnnotationValue() throws Exception {
        OWLAxiom oldReference = dataFactory.getOWLAnnotationAssertionAxiom(
                dataFactory.getRDFSSeeAlso(), CHILD_IRI, OLD_IRI);
        OWLAxiom newReference = dataFactory.getOWLAnnotationAssertionAxiom(
                dataFactory.getRDFSSeeAlso(), CHILD_IRI, NEW_IRI);
        DiffResult diff = diff(
                Set.of(classDeclaration(OLD_IRI), oldReference),
                Set.of(classDeclaration(NEW_IRI), newReference));

        ChangeCandidate candidate = generator.generate(diff).findFirst().orElseThrow();
        assertEquals(Set.of(classDeclaration(OLD_IRI), oldReference), candidate.removedEvidence());
        assertEquals(Set.of(classDeclaration(NEW_IRI), newReference), candidate.addedEvidence());
    }

    @Test(timeout = 10000)
    public void handlesManyRenamesAmongUnrelatedAxioms() throws Exception {
        Set<OWLAxiom> oldAxioms = new HashSet<>();
        Set<OWLAxiom> newAxioms = new HashSet<>();
        for (int i = 0; i < 250; i++) {
            IRI oldIri = IRI.create(NS + "Old" + i);
            IRI newIri = IRI.create(NS + "New" + i);
            oldAxioms.add(classDeclaration(oldIri));
            oldAxioms.add(label(oldIri, "Label " + i));
            newAxioms.add(classDeclaration(newIri));
            newAxioms.add(label(newIri, "Label " + i));
        }
        for (int i = 0; i < 500; i++) {
            oldAxioms.add(label(IRI.create(NS + "UnrelatedOld" + i), "Old " + i));
            newAxioms.add(label(IRI.create(NS + "UnrelatedNew" + i), "New " + i));
        }

        assertEquals(250, generator.generate(directDiff(oldAxioms, newAxioms)).count());
    }

    @Test
    public void doesNotConfuseLabelChangeWithIriRename() throws Exception {
        DiffResult diff = diff(Set.of(classDeclaration(OLD_IRI), label(OLD_IRI, "Old label")),
                                Set.of(classDeclaration(OLD_IRI), label(OLD_IRI, "New label")));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    @Test
    public void rejectsRenameCombinedWithAnotherAxiomChange() throws Exception {
        DiffResult diff = diff(Set.of(classDeclaration(OLD_IRI), label(OLD_IRI, "Old label")),
                                Set.of(classDeclaration(NEW_IRI), label(NEW_IRI, "New label")));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    @Test
    public void rejectsDeclarationOnlyPair() throws Exception {
        DiffResult diff = diff(Set.of(classDeclaration(OLD_IRI)), Set.of(classDeclaration(NEW_IRI)));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    @Test
    public void rejectsAmbiguousPairings() throws Exception {
        IRI oldSecond = IRI.create(NS + "OldSecond");
        IRI newSecond = IRI.create(NS + "NewSecond");

        DiffResult diff = diff(
                Set.of(classDeclaration(OLD_IRI),
                        label(OLD_IRI, "Same label"),
                        classDeclaration(oldSecond),
                        label(oldSecond, "Same label")),
                Set.of(
                        classDeclaration(NEW_IRI),
                        label(NEW_IRI, "Same label"),
                        classDeclaration(newSecond),
                        label(newSecond, "Same label")));

        assertTrue(generator.generate(diff).toList().isEmpty());
    }

    private OWLAxiom classDeclaration(IRI iri) {
        return dataFactory.getOWLDeclarationAxiom(
                dataFactory.getOWLClass(iri));
    }

    private OWLAxiom objectPropertyDeclaration(IRI iri) {
        return dataFactory.getOWLDeclarationAxiom(
                dataFactory.getOWLObjectProperty(iri));
    }

    private OWLAxiom objectPropertyDomain(IRI property, IRI domain) {
        return dataFactory.getOWLObjectPropertyDomainAxiom(
                dataFactory.getOWLObjectProperty(property),
                dataFactory.getOWLClass(domain));
    }

    private OWLAxiom label(IRI subject, String text) {
        return dataFactory.getOWLAnnotationAssertionAxiom(
                dataFactory.getRDFSLabel(),
                subject,
                dataFactory.getOWLLiteral(text, "en"));
    }

    private OWLAxiom subclass(IRI child, IRI parent) {
        return dataFactory.getOWLSubClassOfAxiom(
                dataFactory.getOWLClass(child),
                dataFactory.getOWLClass(parent));
    }

    private DiffResult diff(Set<OWLAxiom> oldAxioms, Set<OWLAxiom> newAxioms) throws Exception {
        OWLOntologyManager oldManager = OWLManager.createOWLOntologyManager();
        OWLOntologyManager newManager = OWLManager.createOWLOntologyManager();
        OWLOntology original = oldManager.createOntology();
        OWLOntology update = newManager.createOntology();
        oldManager.addAxioms(original, oldAxioms);
        newManager.addAxioms(update, newAxioms);

        return new OWLDiffOntologyDiffService().diff(
                original, update, Engine.SYNTACTIC);
    }

    private DiffResult directDiff(Set<OWLAxiom> oldAxioms, Set<OWLAxiom> newAxioms) throws Exception {
        OWLOntologyManager oldManager = OWLManager.createOWLOntologyManager();
        OWLOntologyManager newManager = OWLManager.createOWLOntologyManager();
        OWLOntology original = oldManager.createOntology();
        OWLOntology update = newManager.createOntology();
        oldManager.addAxioms(original, oldAxioms);
        newManager.addAxioms(update, newAxioms);
        return DiffResult.builder()
                .original(original)
                .update(update)
                .engine(Engine.SYNTACTIC)
                .onlyInOriginal(List.copyOf(oldAxioms))
                .onlyInUpdate(List.copyOf(newAxioms))
                .build();
    }
}
