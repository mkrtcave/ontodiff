package cz.cvut.fel.ontodiff.statesearch;

import org.semanticweb.owlapi.model.OWLAxiom;

import java.util.HashSet;
import java.util.Set;

public record StateKey(
        Set<OWLAxiom> remainingRemoved,
        Set<OWLAxiom> remainingAdded
) {
    public StateKey {
        remainingRemoved = Set.copyOf(remainingRemoved);
        remainingAdded = Set.copyOf(remainingAdded);
    }

    static StateKey from(SearchState state){
        return new StateKey(
                state.unexplainedRemoved(),
                state.unexplainedAdded()
        );
    }
}
