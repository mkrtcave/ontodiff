package cz.cvut.fel.ontodiff.statesearch;

import cz.cvut.fel.ontodiff.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import org.semanticweb.owlapi.model.OWLAxiom;

import java.util.*;

public class StateSpaceSearch {

    private List<ChangeCandidate> findApplicableCandidates(SearchState state, List<ChangeCandidate> candidates) {
        return candidates.stream().filter(state::canApply).toList();
    }

    private List<SearchState> createChildStates(SearchState state, List<ChangeCandidate> applicableCandidates) {
        return applicableCandidates.stream().map(state::apply).toList();
    }

        /*

                        initial state
                    removed=3, added=3
                      /       |       \
                     /        |        \
          move Influenza  move Cold  move Pneumonia
               │             │             │
          child state    child state    child state
          cost = 1       cost = 1       cost = 1
     */

    public SearchState search (DiffResult diff, List<ChangeCandidate> candidates) {

        ensureAllDifferencesHaveCandidate(diff, candidates);

        SearchState initialState = SearchState.initial(diff);

        PriorityQueue<SearchState> frontier = new PriorityQueue<>(Comparator.comparingInt(SearchState::cost));

        frontier.add(initialState);

        while (!frontier.isEmpty()) {
            SearchState currentState = frontier.poll();

//            System.out.println(
//                    "Visiting state with cost "
//                            + currentState.cost()
//            );
//
//            System.out.println(
//                    "Remaining removed: "
//                            + currentState.unexplainedRemoved().size()
//            );
//
//            System.out.println(
//                    "Remaining added: "
//                            + currentState.unexplainedAdded().size()
//            );

            if (currentState.isGoal()) {
                return currentState;
            }

            List<ChangeCandidate> applicableCandidates = findApplicableCandidates(currentState, candidates);

            List<SearchState> childStates = createChildStates(currentState, applicableCandidates);

            frontier.addAll(childStates);
        }
        throw new IllegalStateException(
                "No combination of candidates explains all differences"
        );
    }

    private void ensureAllDifferencesHaveCandidate(DiffResult diff, List<ChangeCandidate> candidates) {
        Set<OWLAxiom> uncoveredRemove = new HashSet<>(diff.getOnlyInOriginal());

        Set<OWLAxiom> uncoveredAdd = new HashSet<>(diff.getOnlyInUpdate());

        for (ChangeCandidate candidate : candidates) {
            uncoveredRemove.removeAll(candidate.removedEvidence());
            uncoveredAdd.removeAll(candidate.addedEvidence());
        }

        if (!uncoveredRemove.isEmpty() || !uncoveredAdd.isEmpty()) {
            throw new IllegalStateException(
                    "Search cannot reach a goal state. " + "Uncovered removed axioms: "
                            + uncoveredRemove.size() + ", uncovered added axioms: " + uncoveredAdd.size()
            );
        }
    }
}
