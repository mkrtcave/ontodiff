package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.Set;
import java.util.stream.Stream;

public class NodeCreationCandidateGenerator
        implements CandidateGenerator {

    @Override
    public Stream<ChangeCandidate> generate(
            DiffResult diff
    ) {
        return diff.getOnlyInUpdate()
                .stream()
                .filter(OWLDeclarationAxiom.class::isInstance)
                .map(OWLDeclarationAxiom.class::cast)
                .filter(axiom ->
                        axiom.getEntity().isOWLClass()
                )
                .filter(axiom ->
                        isNewClass(diff, axiom)
                )
                .map(this::createCandidate);
    }

    private boolean isNewClass(DiffResult diff, OWLDeclarationAxiom declaration) {
        IRI classIri = declaration.getEntity().getIRI();

        return !diff.getOriginal().containsClassInSignature(classIri, Imports.INCLUDED);
    }

    private ChangeCandidate createCandidate(
            OWLDeclarationAxiom declaration
    ) {
        String classIri = declaration.getEntity().getIRI().toString();

        HighLevelDiffService.NodeCreation creation = new HighLevelDiffService.NodeCreation(classIri);

        Set<OWLAxiom> addedEvidence = Set.of(declaration);

        return new ChangeCandidate(
                creation,
                Set.of(),
                addedEvidence,
                1.0,
                1,
                "new-class-not-in-original-signature"
        );
    }
}
