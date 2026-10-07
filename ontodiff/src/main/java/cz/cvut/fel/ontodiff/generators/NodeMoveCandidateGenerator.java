package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.EdgeFact;
import cz.cvut.fel.ontodiff.SimpleEdgeExtractor;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class NodeMoveCandidateGenerator implements CandidateGenerator{

    private static final String SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";

    private final SimpleEdgeExtractor edgeExtractor = new SimpleEdgeExtractor();

    public Stream<ChangeCandidate> generate(DiffResult diff){
        List<EdgeFact> removedEdges = diff.getOnlyInOriginal()
                .stream()
                .map(edgeExtractor::extract)
                .flatMap(Optional::stream)
                .toList();

        List<EdgeFact> addedEdges = diff.getOnlyInUpdate()
                .stream()
                .map(edgeExtractor::extract)
                .flatMap(Optional::stream)
                .toList();

        List<ChangeCandidate> candidates = new ArrayList<>();

        for (EdgeFact removed : removedEdges) {
            for (EdgeFact added : addedEdges) {
                if (isPossibleMove(removed, added)) {
                    candidates.add(createCandidate(removed, added));
                }
            }
        }
        return candidates.stream();
    }


    private boolean isPossibleMove(EdgeFact removed, EdgeFact added) {
        return removed.predicate().equals(SUBCLASS_OF)
                && added.predicate().equals(SUBCLASS_OF)
                && removed.subject().equals(added.subject())
                && !removed.object().equals(added.object());
    }

    private ChangeCandidate createCandidate(
            EdgeFact removed,
            EdgeFact added
    ) {
        HighLevelDiffService.NodeMove move = new HighLevelDiffService.NodeMove(
                removed.subject(),
                removed.object(),
                added.object()
        );

        return new ChangeCandidate(
                move,
                Set.of(removed.sourceAxiom()),
                Set.of(added.sourceAxiom()),
                1.0,
                1,
                "same-child-different-parent"
        );
    }
}
