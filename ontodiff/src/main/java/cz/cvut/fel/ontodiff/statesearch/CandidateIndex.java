package cz.cvut.fel.ontodiff.statesearch;

import cz.cvut.fel.ontodiff.ChangeCandidate;
import org.semanticweb.owlapi.model.OWLAxiom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CandidateIndex {

    private final Map<OWLAxiom, List<ChangeCandidate>>
            byRemovedAnchor = new HashMap<>();

    private final Map<OWLAxiom, List<ChangeCandidate>>
            byAddedAnchor = new HashMap<>();

    public CandidateIndex(List<ChangeCandidate> candidates) {
        for (ChangeCandidate candidate : candidates) {
            index(candidate);
        }
    }

    private void index(ChangeCandidate candidate) {
        if (!candidate.removedEvidence().isEmpty()) {
            OWLAxiom anchor = candidate.removedEvidence()
                    .iterator()
                    .next();

            byRemovedAnchor
                    .computeIfAbsent(
                            anchor,
                            ignored -> new ArrayList<>()
                    )
                    .add(candidate);

            return;
        }

        if (!candidate.addedEvidence().isEmpty()) {
            OWLAxiom anchor = candidate.addedEvidence()
                    .iterator()
                    .next();

            byAddedAnchor
                    .computeIfAbsent(
                            anchor,
                            ignored -> new ArrayList<>()
                    )
                    .add(candidate);

            return;
        }

        throw new IllegalArgumentException(
                "Candidate must contain at least one evidence axiom: "
                        + candidate
        );
    }

    public List<ChangeCandidate> findApplicable(SearchState state) {
        List<ChangeCandidate> applicable =
                new ArrayList<>();

        findUsingRemovedEvidence(
                state,
                applicable
        );

        findUsingAddedEvidence(
                state,
                applicable
        );

        return applicable;
    }

    private void findUsingRemovedEvidence(SearchState state, List<ChangeCandidate> applicable) {
        for (OWLAxiom remaining :
                state.unexplainedRemoved()) {

            List<ChangeCandidate> indexedCandidates =
                    byRemovedAnchor.getOrDefault(
                            remaining,
                            List.of()
                    );

            addApplicable(
                    state,
                    indexedCandidates,
                    applicable
            );
        }
    }

    private void findUsingAddedEvidence(SearchState state, List<ChangeCandidate> applicable) {
        for (OWLAxiom remaining :
                state.unexplainedAdded()) {

            List<ChangeCandidate> indexedCandidates =
                    byAddedAnchor.getOrDefault(
                            remaining,
                            List.of()
                    );

            addApplicable(
                    state,
                    indexedCandidates,
                    applicable
            );
        }
    }

    private void addApplicable(SearchState state, List<ChangeCandidate> candidates, List<ChangeCandidate> applicable) {
        for (ChangeCandidate candidate : candidates) {
            if (state.canApply(candidate)) {
                applicable.add(candidate);
            }
        }
    }
}
