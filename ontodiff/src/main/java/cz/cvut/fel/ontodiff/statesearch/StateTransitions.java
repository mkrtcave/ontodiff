package cz.cvut.fel.ontodiff.statesearch;

import cz.cvut.fel.ontodiff.ChangeCandidate;
import org.semanticweb.owlapi.model.OWLAxiom;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StateTransitions {

    /*
    OWL diff returns:
        r1 = removed Flu subClassOf Disease
        r2 = removed Flu rdfs:label 'Flu'
        a1 = added   Flu subClassOf Infection

    Initial state:
        unexplainedRemoved = {r1, r2}
        unexplainedAdded   = {a1}
        selectedCandidates = {}
        cost               = 0

    And then NodeMove contains:
        change = NodeMove(Flu, Disease, Infection)
        removedEvidence = { r1 }
        addedEvidence = { a1 }
        cost = 1

    {r1, r2} contain { r1 }
    {a1 } contain { a1 }

    After NodeMove candidate apply
        removed = {r1, r2 }
        added = {a1}
        selected 0
        cost 0

    Removed evidence = {r1}
    Added evidence = {a1}

    So we have new state:

        removed = {r2 }
        added 0
        selected = NodeMove
        cost 1
     */

    public static boolean isApplicable(SearchState state, ChangeCandidate candidate){
        return state.unexplainedRemoved()
                .containsAll(candidate.removedEvidence())
                && state.unexplainedAdded()
                .containsAll(candidate.addedEvidence());
    }

    public static SearchState apply(
            SearchState state,
            ChangeCandidate candidate
    ) {
        if (!isApplicable(state, candidate)) {
            throw new IllegalArgumentException(
                    "Candidate is not applicable to this state"
            );
        }

        Set<OWLAxiom> remainingRemoved =
                new HashSet<>(state.unexplainedRemoved());

        Set<OWLAxiom> remainingAdded =
                new HashSet<>(state.unexplainedAdded());

        remainingRemoved.removeAll(candidate.removedEvidence());
        remainingAdded.removeAll(candidate.addedEvidence());

        List<ChangeCandidate> selected =
                new ArrayList<>(state.selectedCandidates());

        selected.add(candidate);

        return new SearchState(
                remainingRemoved,
                remainingAdded,
                selected,
                state.cost() + candidate.cost()
        );
    }
}
