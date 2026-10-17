package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.changes.ProposedChange;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class CommentAdditionRemovalCandidateGenerator implements CandidateGenerator {

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        Stream<ChangeCandidate> additions = diff.getOnlyInUpdate().stream()
                .map(axiom -> candidate(axiom, true))
                .flatMap(Optional::stream);
        Stream<ChangeCandidate> removals = diff.getOnlyInOriginal().stream()
                .map(axiom -> candidate(axiom, false))
                .flatMap(Optional::stream);

        return Stream.concat(additions, removals);
    }

    private Optional<ChangeCandidate> candidate(OWLAxiom axiom, boolean added) {
        if (!(axiom instanceof OWLAnnotationAssertionAxiom annotation)
                || !OWLRDFVocabulary.RDFS_COMMENT.getIRI().equals(annotation.getProperty().getIRI())
                || !(annotation.getSubject() instanceof IRI subjectIri)
                || !(annotation.getValue() instanceof OWLLiteral literal)) {
            return Optional.empty();
        }

        ProposedChange change = added
                ? new HighLevelDiffService.CommentAddition(subjectIri.toString(), literal.getLiteral())
                : new HighLevelDiffService.CommentRemoval(subjectIri.toString(), literal.getLiteral());

        return Optional.of(new ChangeCandidate(
                change,
                added ? Set.of() : Set.of(axiom),
                added ? Set.of(axiom) : Set.of(),
                1.0,
                1,
                added ? "comment-added" : "comment-removed"
        ));
    }
}
