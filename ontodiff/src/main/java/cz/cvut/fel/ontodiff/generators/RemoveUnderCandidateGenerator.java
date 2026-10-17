package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import cz.cvut.fel.ontodiff.utils.SimpleEdgeExtractor;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class RemoveUnderCandidateGenerator implements CandidateGenerator {

    private final SimpleEdgeExtractor extractor = new SimpleEdgeExtractor();

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        return diff.getOnlyInOriginal().stream()
                .map(extractor::extract)
                .flatMap(Optional::stream)
                .filter(edge -> SimpleEdgeExtractor.SUBCLASS_OF.equals(edge.predicate()))
                .map(edge -> new ChangeCandidate(
                        new HighLevelDiffService.RemoveUnder(
                                edge.subject(), edge.object()),
                        Set.of(edge.sourceAxiom()),
                        Set.of(),
                        1.0,
                        1,
                        "removed-subclass"
                ));
    }
}
