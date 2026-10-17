package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class NodeRenameCandidateGenerator implements CandidateGenerator {

    private record LabelKey(IRI classIri, String language, IRI datatypeIri) {}

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        Map<LabelKey, List<OWLAnnotationAssertionAxiom>> removed = labelsByClassAndLanguage(diff.getOnlyInOriginal(), diff);
        Map<LabelKey, List<OWLAnnotationAssertionAxiom>> added = labelsByClassAndLanguage(diff.getOnlyInUpdate(), diff);

        List<ChangeCandidate> candidates = new ArrayList<>();

        for (Map.Entry<LabelKey, List<OWLAnnotationAssertionAxiom>> entry : removed.entrySet()) {
            List<OWLAnnotationAssertionAxiom> oldLabels = entry.getValue();
            List<OWLAnnotationAssertionAxiom> newLabels =
                    added.getOrDefault(entry.getKey(), List.of());

            // Multiple changed labels for one class/language have no unique pairing.
            if (oldLabels.size() != 1 || newLabels.size() != 1) {
                continue;
            }

            OWLAnnotationAssertionAxiom oldAxiom = oldLabels.get(0);
            OWLAnnotationAssertionAxiom newAxiom = newLabels.get(0);
            String oldLabel = ((OWLLiteral) oldAxiom.getValue()).getLiteral();
            String newLabel = ((OWLLiteral) newAxiom.getValue()).getLiteral();

            if (oldLabel.equals(newLabel)) {
                continue;
            }

            candidates.add(new ChangeCandidate(
                    new HighLevelDiffService.NodeRename(
                            entry.getKey().classIri().toString(), oldLabel, newLabel),
                    Set.of(oldAxiom),
                    Set.of(newAxiom),
                    1.0,
                    1,
                    "same-class-label-language-datatype"
            ));
        }

        return candidates.stream();
    }

    private Map<LabelKey, List<OWLAnnotationAssertionAxiom>> labelsByClassAndLanguage(
            List<OWLAxiom> axioms,
            DiffResult diff
    ) {
        Map<LabelKey, List<OWLAnnotationAssertionAxiom>> result = new HashMap<>();

        for (OWLAxiom axiom : axioms) {
            labelKey(axiom, diff).ifPresent(key ->
                    result.computeIfAbsent(key, ignored -> new ArrayList<>())
                            .add((OWLAnnotationAssertionAxiom) axiom));
        }

        return result;
    }

    private Optional<LabelKey> labelKey(OWLAxiom axiom, DiffResult diff) {
        if (!(axiom instanceof OWLAnnotationAssertionAxiom annotation)
                || !OWLRDFVocabulary.RDFS_LABEL.getIRI().equals(annotation.getProperty().getIRI())
                || !(annotation.getSubject() instanceof IRI classIri)
                || !(annotation.getValue() instanceof OWLLiteral literal)
                || !diff.getOriginal().containsClassInSignature(classIri, Imports.INCLUDED)
                || !diff.getUpdate().containsClassInSignature(classIri, Imports.INCLUDED)) {
            return Optional.empty();
        }

        return Optional.of(new LabelKey(
                classIri,
                literal.getLang(),
                literal.getDatatype().getIRI()
        ));
    }
}
