package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.utils.EdgeFact;
import cz.cvut.fel.ontodiff.utils.SimpleEdgeExtractor;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;

import java.util.Set;
import java.util.stream.Stream;
import java.util.Optional;

public class EdgeDeletionCandidateGenerator implements CandidateGenerator {

    private final SimpleEdgeExtractor edgeExtractor = new SimpleEdgeExtractor();

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        return diff.getOnlyInOriginal()
                .stream()
                .map(edgeExtractor::extract)
                .flatMap(Optional::stream)
                .map(this::createCandidate);
    }

    private ChangeCandidate createCandidate(EdgeFact edge) {
        HighLevelDiffService.EdgeDeletion deletion =
                new HighLevelDiffService.EdgeDeletion(
                        edge.subject(),
                        edge.predicate(),
                        edge.object()
                );

        return new ChangeCandidate(
                deletion,
                Set.of(edge.sourceAxiom()),
                Set.of(),
                1.0,
                1,
                "removed-simple-edge"
        );
    }
}
