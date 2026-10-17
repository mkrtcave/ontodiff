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

        List<ChangeCandidate> candidates = new ArrayList<>();

        diff.getOnlyInOriginal().stream()
                .map(extractor::extract)
                .flatMap(Optional::stream)
                .forEach(removed -> {
                    HighLevelDiffService.SrcTgt key = new HighLevelDiffService.SrcTgt(removed.subject(), removed.object());

                    for (EdgeFact added : addedByEndpoints.getOrDefault(key, List.of())) {
                        if (removed.predicate().equals(added.predicate())) {
                            continue;
                        }
                        candidates.add(new ChangeCandidate(
                                new HighLevelDiffService.PredicateChange(
                                        removed.subject(),
                                        removed.object(),
                                        removed.predicate(),
                                        added.predicate()
                                ),
                                Set.of(removed.sourceAxiom()),
                                Set.of(added.sourceAxiom()),
                                0.8,
                                1,
                                "same-endpoints-different-predicate"
                        ));
                    }
                });

        return candidates.stream();
    }
}
