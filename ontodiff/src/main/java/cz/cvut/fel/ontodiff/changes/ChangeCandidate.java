package cz.cvut.fel.ontodiff.changes;

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
        if (!Double.isFinite(confidence) || confidence <= 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "Candidate confidence must be finite and in the interval (0, 1]: "
                            + confidence
            );
        }
        removedEvidence = Set.copyOf(removedEvidence);
        addedEvidence = Set.copyOf(addedEvidence);
    }
}
