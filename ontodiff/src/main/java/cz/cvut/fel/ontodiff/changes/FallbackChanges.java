package cz.cvut.fel.ontodiff.changes;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.statesearch.SearchState;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FallbackChanges {

    public static List<ChangeCandidate> create(DiffResult diff, SearchState selectedState) {
        Set<OWLAxiom> remainingRemoved = new LinkedHashSet<>(diff.getOnlyInOriginal());

        Set<OWLAxiom> remainingAdded = new LinkedHashSet<>(diff.getOnlyInUpdate());

        for (ChangeCandidate selected : selectedState.selectedCandidates()) {
            remainingRemoved.removeAll(selected.removedEvidence());
            remainingAdded.removeAll(selected.addedEvidence());
        }

        List<ChangeCandidate> fallbacks = new ArrayList<>();

        for (OWLAxiom axiom : remainingRemoved) {
            ProposedChange change = axiom instanceof OWLAnnotationAssertionAxiom annotation
                            ? new FallbackChange.AnnotationRemoval(annotation)
                            : new FallbackChange.AxiomRemoval(axiom);

            fallbacks.add(new ChangeCandidate(
                    change,
                    Set.of(axiom),
                    Set.of(),
                    1.0,
                    1,
                    "fallback-removal"
            ));
        }

        for (OWLAxiom axiom : remainingAdded) {
            ProposedChange change = axiom instanceof OWLAnnotationAssertionAxiom annotation
                            ? new FallbackChange.AnnotationAddition(annotation)
                            : new FallbackChange.AxiomAddition(axiom);

            fallbacks.add(new ChangeCandidate(
                    change,
                    Set.of(),
                    Set.of(axiom),
                    1.0,
                    1,
                    "fallback-addition"
            ));
        }

        return fallbacks;
    }
}
