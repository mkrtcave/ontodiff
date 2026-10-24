package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

 public class NodeObsoletionCandidateGenerator implements CandidateGenerator {

    private static final IRI DEPRECATED = OWLRDFVocabulary.OWL_DEPRECATED.getIRI();
    private static final IRI DIRECT_REPLACEMENT = IRI.create("http://purl.obolibrary.org/obo/IAO_0100001");

    private record Replacement(String value, OWLAnnotationAssertionAxiom axiom) {}

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        Map<IRI, Set<OWLAxiom>> removedFalseByNode = removedFalseAssertions(diff);
        Map<IRI, List<Replacement>> addedReplacementsByNode = addedReplacements(diff);
        List<ChangeCandidate> candidates = new ArrayList<>();

        for (OWLAxiom axiom : diff.getOnlyInUpdate()) {
            Optional<IRI> deprecatedNode = deprecatedNode(axiom, true);
            if (deprecatedNode.isEmpty()) {
                continue;
            }

            IRI nodeIri = deprecatedNode.get();
            if (!isExistingNode(diff, nodeIri) || isDeprecated(diff, nodeIri)) {
                continue;
            }

            Set<OWLAxiom> removedEvidence =
                    removedFalseByNode.getOrDefault(nodeIri, Set.of());
            Set<OWLAxiom> obsoletionEvidence = Set.of(axiom);

            candidates.add(new ChangeCandidate(
                    new HighLevelDiffService.NodeObsoletion(
                            nodeIri.toString(),
                            "deprecated-flag-added"),
                    removedEvidence,
                    obsoletionEvidence,
                    CandidateConfidence.DIRECT,
                    1,
                    "owl-deprecated-true-added"
            ));

            List<Replacement> replacements =
                    addedReplacementsByNode.getOrDefault(nodeIri, List.of());

            // KGCL permits exactly one direct replacement. If the source has several,
            // leave those assertions to fallbacks rather than choosing arbitrarily.
            if (replacements.size() == 1) {
                Replacement replacement = replacements.get(0);
                Set<OWLAxiom> addedEvidence = new HashSet<>(obsoletionEvidence);
                addedEvidence.add(replacement.axiom());

                candidates.add(new ChangeCandidate(
                        new HighLevelDiffService.NodeObsoletionWithDirectReplacement(
                                nodeIri.toString(),
                                replacement.value()),
                        removedEvidence,
                        addedEvidence,
                        CandidateConfidence.UNAMBIGUOUS_PAIR,
                        1,
                        "owl-deprecated-with-iao-direct-replacement"
                ));
            }
        }

        return candidates.stream();
    }

    private Map<IRI, Set<OWLAxiom>> removedFalseAssertions(DiffResult diff) {
        Map<IRI, Set<OWLAxiom>> result = new HashMap<>();

        for (OWLAxiom axiom : diff.getOnlyInOriginal()) {
            deprecatedNode(axiom, false).ifPresent(nodeIri ->
                    result.computeIfAbsent(nodeIri, ignored -> new HashSet<>())
                            .add(axiom));
        }

        return result;
    }

    private Map<IRI, List<Replacement>> addedReplacements(DiffResult diff) {
        Map<IRI, List<Replacement>> result = new HashMap<>();

        for (OWLAxiom axiom : diff.getOnlyInUpdate()) {
            replacement(axiom).ifPresent(entry ->
                    result.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                            .add(entry.getValue()));
        }

        return result;
    }

    private Optional<Map.Entry<IRI, Replacement>> replacement(OWLAxiom axiom) {
        if (!(axiom instanceof OWLAnnotationAssertionAxiom annotation)
                || !DIRECT_REPLACEMENT.equals(annotation.getProperty().getIRI())
                || !(annotation.getSubject() instanceof IRI nodeIri)) {
            return Optional.empty();
        }

        Optional<String> value = nodeReference(annotation.getValue());
        return value.map(replacement -> Map.entry(
                nodeIri,
                new Replacement(replacement, annotation)));
    }

    private Optional<String> nodeReference(OWLAnnotationValue value) {
        if (value instanceof IRI iri) {
            return Optional.of(iri.toString());
        }

        if (value instanceof OWLLiteral literal && !literal.getLiteral().isBlank()) {
            // OBO ontologies in the wild also encode this value as xsd:anyURI
            // literal or as a CURIE string so retain that lexical form.
            return Optional.of(literal.getLiteral());
        }

        return Optional.empty();
    }

    private Optional<IRI> deprecatedNode(OWLAxiom axiom, boolean expectedValue) {
        if (!(axiom instanceof OWLAnnotationAssertionAxiom annotation)
                || !DEPRECATED.equals(annotation.getProperty().getIRI())
                || !(annotation.getSubject() instanceof IRI nodeIri)
                || !(annotation.getValue() instanceof OWLLiteral literal)
                || !literal.isBoolean()
                || literal.parseBoolean() != expectedValue) {
            return Optional.empty();
        }

        return Optional.of(nodeIri);
    }

    private boolean isExistingNode(DiffResult diff, IRI nodeIri) {
        return diff.getOriginal().containsEntityInSignature(nodeIri, Imports.INCLUDED)
                && diff.getUpdate().containsEntityInSignature(nodeIri, Imports.INCLUDED);
    }

    private boolean isDeprecated(DiffResult diff, IRI nodeIri) {
        return diff.getOriginal().getImportsClosure().stream()
                .flatMap(ontology -> ontology.getAnnotationAssertionAxioms(nodeIri).stream())
                .anyMatch(axiom -> deprecatedNode(axiom, true).isPresent());
    }
}
