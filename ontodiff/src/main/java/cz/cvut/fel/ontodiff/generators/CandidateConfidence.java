package cz.cvut.fel.ontodiff.generators;

/**
 * Initial heuristic confidence policy for generated change candidates.
 * These values are ranking signals, not calibrated probabilities.
 */
public final class CandidateConfidence {

    public static final double DIRECT = 1.0;
    public static final double UNAMBIGUOUS_PAIR = 0.95;
    public static final double STRUCTURAL_INFERENCE = 0.9;
    public static final double WEAK_INTERPRETATION = 0.8;

    private CandidateConfidence() {
    }

    public static double adjustedForAmbiguity(double baseConfidence, int compatiblePairings) {
        if (!Double.isFinite(baseConfidence) || baseConfidence <= 0.0 || baseConfidence > 1.0) {
            throw new IllegalArgumentException("Base confidence must be finite and in (0, 1]: " + baseConfidence);
        }
        if (compatiblePairings < 1) {
            throw new IllegalArgumentException("Compatible pairing count must be positive: " + compatiblePairings);
        }
        return baseConfidence / compatiblePairings;
    }
}
