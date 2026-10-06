package cz.cvut.fel.ontodiff;

import org.semanticweb.owlapi.model.OWLAxiom;

import java.util.Set;

public record ChangeCandidate (
    ProposedChange change,
    Set<OWLAxiom> removedEvidence,
    Set<OWLAxiom> addedEvidence,
    double confidence,
    int cost,
    String rule
) {
    public ChangeCandidate {
        removedEvidence = Set.copyOf(removedEvidence);
        addedEvidence = Set.copyOf(addedEvidence);
    }
}
