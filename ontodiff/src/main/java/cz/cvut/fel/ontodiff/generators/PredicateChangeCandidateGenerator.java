package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import cz.cvut.fel.ontodiff.utils.EdgeFact;
import cz.cvut.fel.ontodiff.utils.SimpleEdgeExtractor;

import java.util.*;
import java.util.stream.Stream;

public class PredicateChangeCandidateGenerator implements CandidateGenerator {

    private final SimpleEdgeExtractor extractor = new SimpleEdgeExtractor();

    private record PredicatePair(
            HighLevelDiffService.SrcTgt endpoints,
            EdgeFact removed,
            EdgeFact added
    ) {}

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        Map<HighLevelDiffService.SrcTgt, List<EdgeFact>> addedByEndpoints =
                new HashMap<>();

        diff.getOnlyInUpdate().stream()
                .map(extractor::extract)
                .flatMap(Optional::stream)
                .forEach(added -> {
                            HighLevelDiffService.SrcTgt key =
                            new HighLevelDiffService.SrcTgt(added.subject(), added.object());
                    addedByEndpoints.computeIfAbsent(key, ignored -> new ArrayList<>()).add(added);
                });

        List<PredicatePair> compatiblePairs = new ArrayList<>();
        Map<HighLevelDiffService.SrcTgt, Integer> pairCountByEndpoints =
                new HashMap<>();

        diff.getOnlyInOriginal().stream()
                .map(extractor::extract)
                .flatMap(Optional::stream)
                .forEach(removed -> {
                    HighLevelDiffService.SrcTgt key = new HighLevelDiffService.SrcTgt(removed.subject(), removed.object());

                    for (EdgeFact added : addedByEndpoints.getOrDefault(key, List.of())) {
                        if (removed.predicate().equals(added.predicate())) {
                            continue;
                        }
                        compatiblePairs.add(new PredicatePair(key, removed, added));
                        pairCountByEndpoints.merge(key, 1, Integer::sum);
                    }
                });

        return compatiblePairs.stream()
                .map(pair -> createCandidate(
                        pair,
                        pairCountByEndpoints.get(pair.endpoints())
                ));
    }

    private ChangeCandidate createCandidate(PredicatePair pair,
                                            int compatiblePairings) {
        EdgeFact removed = pair.removed();
        EdgeFact added = pair.added();

        return new ChangeCandidate(
                new HighLevelDiffService.PredicateChange(
                        removed.subject(),
                        removed.object(),
                        removed.predicate(),
                        added.predicate()
                ),
                Set.of(removed.sourceAxiom()),
                Set.of(added.sourceAxiom()),
                CandidateConfidence.adjustedForAmbiguity(
                        CandidateConfidence.WEAK_INTERPRETATION,
                        compatiblePairings
                ),
                1,
                "same-endpoints-different-predicate"
        );
    }
}
