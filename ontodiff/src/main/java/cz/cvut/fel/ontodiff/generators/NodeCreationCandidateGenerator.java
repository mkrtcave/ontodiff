package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NodeCreationCandidateGenerator implements CandidateGenerator {

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        Set<IRI> originalIris = diff.getOriginal().getSignature(Imports.INCLUDED)
                .stream()
                .map(OWLEntity::getIRI)
                .collect(Collectors.toSet());

        Map<IRI, Set<OWLAxiom>> declarationsByIri = new LinkedHashMap<>();
        Map<IRI, Set<OWLAxiom>> annotationsByIri = new HashMap<>();
        Set<IRI> newlyDeclaredClasses = new HashSet<>();

        for (OWLAxiom axiom : diff.getOnlyInUpdate()) {
            if (axiom instanceof OWLDeclarationAxiom declaration) {
                IRI iri = declaration.getEntity().getIRI();
                if (declaration.getEntity().isOWLClass()) {
                    newlyDeclaredClasses.add(iri);
                } else if (!originalIris.contains(iri)) {
                    declarationsByIri.computeIfAbsent(iri, ignored -> new HashSet<>())
                            .add(declaration);
                }
            } else if (axiom instanceof OWLAnnotationAssertionAxiom annotation
                    && annotation.getSubject() instanceof IRI iri) {
                annotationsByIri.computeIfAbsent(iri, ignored -> new HashSet<>())
                        .add(annotation);
            }
        }

        return declarationsByIri.entrySet().stream()
                .map(entry -> createCandidate(entry.getKey(), entry.getValue(),
                        newlyDeclaredClasses.contains(entry.getKey())
                                ? Set.of() : annotationsByIri.getOrDefault(entry.getKey(), Set.of())));
    }

    private ChangeCandidate createCandidate(IRI iri,
                                            Set<OWLAxiom> declarations,
                                            Set<OWLAxiom> annotations) {
        Set<OWLAxiom> addedEvidence = new HashSet<>(declarations);
        addedEvidence.addAll(annotations);

        return new ChangeCandidate(
                new HighLevelDiffService.NodeCreation(iri.toString()),
                Set.of(),
                addedEvidence,
                CandidateConfidence.DIRECT,
                1,
                "new-non-class-node-and-annotations"
        );
    }
}
