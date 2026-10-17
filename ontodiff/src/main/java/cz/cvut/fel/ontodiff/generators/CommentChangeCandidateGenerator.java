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

public class CommentChangeCandidateGenerator implements CandidateGenerator {

    private record CommentKey(IRI subjectIri, String language, IRI datatypeIri) {}

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        Map<CommentKey, List<OWLAnnotationAssertionAxiom>> removed =
                commentsBySubjectAndLanguage(diff.getOnlyInOriginal(), diff);
        Map<CommentKey, List<OWLAnnotationAssertionAxiom>> added =
                commentsBySubjectAndLanguage(diff.getOnlyInUpdate(), diff);
        List<ChangeCandidate> candidates = new ArrayList<>();

        for (Map.Entry<CommentKey, List<OWLAnnotationAssertionAxiom>> entry : removed.entrySet()) {
            List<OWLAnnotationAssertionAxiom> oldComments = entry.getValue();
            List<OWLAnnotationAssertionAxiom> newComments =
                    added.getOrDefault(entry.getKey(), List.of());

            if (oldComments.size() != 1 || newComments.size() != 1) {
                continue;
            }

            OWLAnnotationAssertionAxiom oldAxiom = oldComments.get(0);
            OWLAnnotationAssertionAxiom newAxiom = newComments.get(0);
            String oldText = ((OWLLiteral) oldAxiom.getValue()).getLiteral();
            String newText = ((OWLLiteral) newAxiom.getValue()).getLiteral();

            if (oldText.equals(newText)) {
                continue;
            }

            candidates.add(new ChangeCandidate(
                    new HighLevelDiffService.CommentChange(
                            entry.getKey().subjectIri().toString(), oldText, newText),
                    Set.of(oldAxiom),
                    Set.of(newAxiom),
                    1.0,
                    1,
                    "same-subject-comment-language-datatype"
            ));
        }

        return candidates.stream();
    }

    private Map<CommentKey, List<OWLAnnotationAssertionAxiom>> commentsBySubjectAndLanguage(
            List<OWLAxiom> axioms,
            DiffResult diff
    ) {
        Map<CommentKey, List<OWLAnnotationAssertionAxiom>> result = new HashMap<>();

        for (OWLAxiom axiom : axioms) {
            commentKey(axiom, diff).ifPresent(key ->
                    result.computeIfAbsent(key, ignored -> new ArrayList<>())
                            .add((OWLAnnotationAssertionAxiom) axiom));
        }

        return result;
    }

    private Optional<CommentKey> commentKey(OWLAxiom axiom, DiffResult diff) {
        if (!(axiom instanceof OWLAnnotationAssertionAxiom annotation)
                || !OWLRDFVocabulary.RDFS_COMMENT.getIRI().equals(annotation.getProperty().getIRI())
                || !(annotation.getSubject() instanceof IRI subjectIri)
                || !(annotation.getValue() instanceof OWLLiteral literal)) {
            return Optional.empty();
        }

        boolean classInOriginal = diff.getOriginal()
                .containsClassInSignature(subjectIri, Imports.INCLUDED);
        boolean classInUpdate = diff.getUpdate()
                .containsClassInSignature(subjectIri, Imports.INCLUDED);
        if (classInOriginal != classInUpdate) {
            return Optional.empty();
        }

        return Optional.of(new CommentKey(
                subjectIri,
                literal.getLang(),
                literal.getDatatype().getIRI()
        ));
    }
}
