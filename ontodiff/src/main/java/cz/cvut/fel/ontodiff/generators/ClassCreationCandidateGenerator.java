package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassCreationCandidateGenerator implements CandidateGenerator {

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        return diff.getOnlyInUpdate()
                .stream()
                .filter(OWLDeclarationAxiom.class::isInstance)
                .map(OWLDeclarationAxiom.class::cast)
                .filter(axiom -> axiom.getEntity().isOWLClass())
                .filter(axiom -> isNewClass(diff, axiom))
                .map(declaration -> createCandidate(diff, declaration));
    }

    private boolean isNewClass(DiffResult diff, OWLDeclarationAxiom declaration) {
        IRI classIri = declaration.getEntity().getIRI();

        return !diff.getOriginal().containsClassInSignature(classIri, Imports.INCLUDED);
    }

    private ChangeCandidate createCandidate(DiffResult diff, OWLDeclarationAxiom declaration) {
        IRI classIri = declaration.getEntity().getIRI();

        Set<OWLAxiom> addedEvidence = diff.getOnlyInUpdate()
                .stream()
                .filter(axiom -> belongsToCreatedClass(axiom, classIri))
                .collect(Collectors.toSet());

        return new ChangeCandidate(
                new HighLevelDiffService.ClassCreation(classIri.toString()),
                Set.of(),
                addedEvidence,
                CandidateConfidence.DIRECT,
                1,
                "new-class-and-owned-axioms"
        );
    }

    private boolean belongsToCreatedClass(OWLAxiom axiom, IRI classIri) {
        if (axiom instanceof OWLDeclarationAxiom declaration) {
            return declaration.getEntity().isOWLClass() && declaration.getEntity().getIRI().equals(classIri);
        }

        if (axiom instanceof OWLAnnotationAssertionAxiom annotation) {
            return classIri.equals(annotation.getSubject());
        }

        if (axiom instanceof OWLSubClassOfAxiom subclass) {
            return !subclass.getSubClass().isAnonymous() && subclass.getSubClass()
                    .asOWLClass().getIRI().equals(classIri);
        }

        return false;
    }

}
