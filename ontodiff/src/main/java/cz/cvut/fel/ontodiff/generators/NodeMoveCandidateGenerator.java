package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.utils.EdgeFact;
import cz.cvut.fel.ontodiff.utils.SimpleEdgeExtractor;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class NodeMoveCandidateGenerator implements CandidateGenerator{

    private static final String SUBCLASS_OF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";

    private record MovePair(EdgeFact removed, EdgeFact added) {}

    private final SimpleEdgeExtractor edgeExtractor = new SimpleEdgeExtractor();

    public Stream<ChangeCandidate> generate(DiffResult diff){
        List<EdgeFact> removedEdges = diff.getOnlyInOriginal()
                .stream()
                .map(edgeExtractor::extract)
                .flatMap(Optional::stream)
                .filter(this::isSubclassEdge)
                .toList();

        Map<String, List<EdgeFact>> addedEdgesByChild = diff.getOnlyInUpdate()
                .stream()
                .map(edgeExtractor::extract)
                .flatMap(Optional::stream)
                .filter(this::isSubclassEdge)
                .collect(Collectors.groupingBy(EdgeFact::subject));

        List<MovePair> compatiblePairs = new ArrayList<>();
        Map<String, Integer> pairCountByChild = new HashMap<>();

        for (EdgeFact removed : removedEdges) {
            for (EdgeFact added : addedEdgesByChild.getOrDefault(
                    removed.subject(), List.of())) {
                if (isPossibleMove(removed, added)) {
                    compatiblePairs.add(new MovePair(removed, added));
                    pairCountByChild.merge(removed.subject(), 1, Integer::sum);
                }
            }
        }

        return compatiblePairs.stream()
                .map(pair -> createCandidate(
                        pair.removed(),
                        pair.added(),
                        pairCountByChild.get(pair.removed().subject())
                ));
    }

    private boolean isSubclassEdge(EdgeFact edge) {
        return edge.predicate().equals(SUBCLASS_OF);
    }

    private boolean isPossibleMove(EdgeFact removed, EdgeFact added) {
        return removed.subject().equals(added.subject())
                && !removed.object().equals(added.object());
    }

    private ChangeCandidate createCandidate(
            EdgeFact removed,
            EdgeFact added,
            int compatiblePairings
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
                CandidateConfidence.adjustedForAmbiguity(
                        CandidateConfidence.STRUCTURAL_INFERENCE,
                        compatiblePairings
                ),
                1,
                "same-child-different-parent"
        );
    }
}
