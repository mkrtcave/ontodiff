package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import cz.cvut.fel.ontodiff.utils.SimpleEdgeExtractor;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class PlaceUnderCandidateGenerator implements CandidateGenerator {
    private final SimpleEdgeExtractor extractor = new SimpleEdgeExtractor();

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        return diff.getOnlyInUpdate().stream()
                .map(extractor::extract)
                .flatMap(Optional::stream)
                .filter(edge -> SimpleEdgeExtractor.SUBCLASS_OF.equals(edge.predicate()))
                .map(edge -> new ChangeCandidate(new HighLevelDiffService.PlaceUnder(
                         edge.subject(), edge.object()),
                        Set.of(),
                        Set.of(edge.sourceAxiom()),
                        CandidateConfidence.DIRECT,
                        1,
                        "added-subclass"
                ));
    }
}
