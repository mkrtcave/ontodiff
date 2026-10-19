package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NodeDeletionCandidateGenerator implements CandidateGenerator {

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        return diff.getOnlyInOriginal()
                .stream()
                .filter(OWLDeclarationAxiom.class::isInstance)
                .map(OWLDeclarationAxiom.class::cast)
                .filter(declaration ->
                        declaration.getEntity().isOWLClass()
                )
                .filter(declaration ->
                        isDeletedClass(diff, declaration)
                )
                .map(declaration ->
                        createCandidate(diff, declaration)
                );
    }

    private boolean isDeletedClass(
            DiffResult diff,
            OWLDeclarationAxiom declaration
    ) {
        IRI classIri = declaration.getEntity().getIRI();

        return !diff.getUpdate()
                .containsClassInSignature(
                        classIri,
                        Imports.INCLUDED
                );
    }

    private ChangeCandidate createCandidate(
            DiffResult diff,
            OWLDeclarationAxiom declaration
    ) {
        IRI classIri = declaration.getEntity().getIRI();

        Set<OWLAxiom> removedEvidence =
                diff.getOnlyInOriginal()
                        .stream()
                        .filter(axiom ->
                                belongsToDeletedClass(
                                        axiom,
                                        classIri
                                )
                        )
                        .collect(Collectors.toSet());

        return new ChangeCandidate(
                new HighLevelDiffService.NodeDeletion(
                        classIri.toString()
                ),
                removedEvidence,
                Set.of(),
                CandidateConfidence.DIRECT,
                1,
                "deleted-class-and-owned-axioms"
        );
    }

    private boolean belongsToDeletedClass(
            OWLAxiom axiom,
            IRI classIri
    ) {
        if (axiom instanceof OWLDeclarationAxiom declaration) {
            return declaration.getEntity().isOWLClass()
                    && declaration.getEntity()
                    .getIRI()
                    .equals(classIri);
        }

        if (axiom instanceof OWLAnnotationAssertionAxiom annotation) {
            return classIri.equals(
                    annotation.getSubject()
            );
        }

        if (axiom instanceof OWLSubClassOfAxiom subclass) {
            return !subclass.getSubClass().isAnonymous()
                    && subclass.getSubClass()
                    .asOWLClass()
                    .getIRI()
                    .equals(classIri);
        }

        return false;
    }
}
