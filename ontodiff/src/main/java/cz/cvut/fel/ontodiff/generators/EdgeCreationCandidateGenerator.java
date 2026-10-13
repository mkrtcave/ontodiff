package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.utils.EdgeFact;
import cz.cvut.fel.ontodiff.utils.SimpleEdgeExtractor;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class EdgeCreationCandidateGenerator implements CandidateGenerator {

    private final SimpleEdgeExtractor edgeExtractor = new SimpleEdgeExtractor();

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        return diff.getOnlyInUpdate().stream()
                .map(edgeExtractor::extract)
                .flatMap(Optional::stream)
                .filter(edge -> !SimpleEdgeExtractor.SUBCLASS_OF.equals(edge.predicate()))
                .map(this::createCandidate);
    }

    private ChangeCandidate createCandidate(EdgeFact edge) {
        HighLevelDiffService.EdgeCreation creation = new HighLevelDiffService.EdgeCreation(
                        edge.subject(),
                        edge.predicate(),
                        edge.object());

        return new ChangeCandidate(
                creation,
                Set.of(),
                Set.of(edge.sourceAxiom()),
                1.0,
                1,
                "added-simple-edge"
        );
    }
}