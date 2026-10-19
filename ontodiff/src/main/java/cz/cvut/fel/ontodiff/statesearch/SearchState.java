package cz.cvut.fel.ontodiff.statesearch;

import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import org.semanticweb.owlapi.model.OWLAxiom;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record SearchState(
        Set<OWLAxiom> unexplainedRemoved,
        Set<OWLAxiom> unexplainedAdded,
        List<ChangeCandidate> selectedCandidates,
        int cost,
        double confidencePenalty
) {
    public SearchState {
        if (!Double.isFinite(confidencePenalty) || confidencePenalty < 0.0) {
            throw new IllegalArgumentException(
                    "Confidence penalty must be finite and non-negative: "
                            + confidencePenalty
            );
        }
        unexplainedRemoved = Set.copyOf(unexplainedRemoved);
        unexplainedAdded = Set.copyOf(unexplainedAdded);
        selectedCandidates = List.copyOf(selectedCandidates);
    }

    public SearchState(
            Set<OWLAxiom> unexplainedRemoved,
            Set<OWLAxiom> unexplainedAdded,
            List<ChangeCandidate> selectedCandidates,
            int cost
    ) {
        this(
                unexplainedRemoved,
                unexplainedAdded,
                selectedCandidates,
                cost,
                selectedCandidates.stream()
                        .mapToDouble(SearchState::confidencePenaltyOf)
                        .sum()
        );
    }

    public static SearchState initial(DiffResult diff) {
        return new SearchState(
                new HashSet<>(diff.getOnlyInOriginal()),
                new HashSet<>(diff.getOnlyInUpdate()),
                List.of(),
                0,
                0.0
        );
    }

    public boolean isGoal() {
        return unexplainedRemoved.isEmpty() && unexplainedAdded.isEmpty();
    }


    public boolean canApply(ChangeCandidate candidate) {
        return unexplainedRemoved.containsAll(candidate.removedEvidence())
                && unexplainedAdded.containsAll(candidate.addedEvidence());
    }

    public SearchState apply(ChangeCandidate candidate) {
        if (!canApply(candidate)) {
            throw new IllegalArgumentException(
                    "Candidate evidence is not available in this state"
            );
        }

        Set<OWLAxiom> remainingRemoved =
                new HashSet<>(unexplainedRemoved);

        Set<OWLAxiom> remainingAdded =
                new HashSet<>(unexplainedAdded);

        remainingRemoved.removeAll(
                candidate.removedEvidence()
        );

        remainingAdded.removeAll(
                candidate.addedEvidence()
        );

        List<ChangeCandidate> selected =
                new ArrayList<>(selectedCandidates);

        selected.add(candidate);

        return new SearchState(
                remainingRemoved,
                remainingAdded,
                selected,
                cost + candidate.cost(),
                confidencePenalty + confidencePenaltyOf(candidate)
        );
    }

    private static double confidencePenaltyOf(ChangeCandidate candidate) {
        return -Math.log(candidate.confidence());
    }
}
