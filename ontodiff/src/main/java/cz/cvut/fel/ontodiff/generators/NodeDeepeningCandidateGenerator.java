package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import cz.cvut.fel.ontodiff.utils.EdgeFact;
import cz.cvut.fel.ontodiff.utils.SimpleEdgeExtractor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates the KGCL NodeDeepening specialization of NodeMove. The changed
 * edge keeps its subject and predicate and changes its object. The new object
 * must be a proper descendant of the old object in the updated relational
 * graph; KGCL permits that path to contain edges with any predicate.
 */
public class NodeDeepeningCandidateGenerator implements CandidateGenerator {

    private static final String IRI_REPRESENTATION = "iri";

    private record MoveKey(String subject, String predicate) {}
    private record MovePair(EdgeFact removed, EdgeFact added) {}

    private final SimpleEdgeExtractor edgeExtractor = new SimpleEdgeExtractor();

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        Map<String, Set<String>> objectsBySubject = relationalGraph(diff);
        Map<String, Set<String>> ancestorCache = new HashMap<>();

        Map<MoveKey, List<EdgeFact>> addedBySubjectAndPredicate =
                diff.getOnlyInUpdate().stream()
                        .map(edgeExtractor::extract)
                        .flatMap(Optional::stream)
                        .collect(Collectors.groupingBy(edge ->
                                new MoveKey(edge.subject(), edge.predicate())));

        List<MovePair> compatiblePairs = new ArrayList<>();
        Map<MoveKey, Integer> pairCountByEdge = new HashMap<>();

        diff.getOnlyInOriginal().stream()
                .map(edgeExtractor::extract)
                .flatMap(Optional::stream)
                .forEach(removed -> {
                    MoveKey key = new MoveKey(
                            removed.subject(), removed.predicate());

                    for (EdgeFact added : addedBySubjectAndPredicate
                            .getOrDefault(key, List.of())) {
                        if (!removed.object().equals(added.object())
                                && isProperDescendant(
                                        added.object(),
                                        removed.object(),
                                        objectsBySubject,
                                        ancestorCache)) {
                            compatiblePairs.add(new MovePair(removed, added));
                            pairCountByEdge.merge(key, 1, Integer::sum);
                        }
                    }
                });

        return compatiblePairs.stream().map(pair -> {
            MoveKey key = new MoveKey(
                    pair.removed().subject(), pair.removed().predicate());
            return createCandidate(pair, pairCountByEdge.get(key));
        });
    }

    private ChangeCandidate createCandidate(
            MovePair pair,
            int compatiblePairings
    ) {
        EdgeFact removed = pair.removed();
        EdgeFact added = pair.added();

        HighLevelDiffService.Edge aboutEdge = new HighLevelDiffService.Edge(
                removed.subject(),
                removed.predicate(),
                removed.object(),
                IRI_REPRESENTATION,
                IRI_REPRESENTATION,
                IRI_REPRESENTATION
        );

        return new ChangeCandidate(
                new HighLevelDiffService.NodeDeepening(
                        removed.object(),
                        added.object(),
                        aboutEdge,
                        IRI_REPRESENTATION,
                        IRI_REPRESENTATION),
                Set.of(removed.sourceAxiom()),
                Set.of(added.sourceAxiom()),
                CandidateConfidence.adjustedForAmbiguity(
                        CandidateConfidence.UNAMBIGUOUS_PAIR,
                        compatiblePairings),
                1,
                "new-object-is-proper-descendant-of-old-object"
        );
    }

    private Map<String, Set<String>> relationalGraph(DiffResult diff) {
        Map<String, Set<String>> result = new HashMap<>();

        diff.getUpdate().getImportsClosure().stream()
                .flatMap(ontology -> ontology.getAxioms().stream())
                .map(edgeExtractor::extract)
                .flatMap(Optional::stream)
                .forEach(edge -> result.computeIfAbsent(
                                edge.subject(), ignored -> new HashSet<>())
                        .add(edge.object()));

        return result;
    }

    private boolean isProperDescendant(
            String possibleDescendant,
            String possibleAncestor,
            Map<String, Set<String>> objectsBySubject,
            Map<String, Set<String>> ancestorCache
    ) {
        return !possibleDescendant.equals(possibleAncestor)
                && reachableFrom(
                        possibleDescendant,
                        objectsBySubject,
                        ancestorCache).contains(possibleAncestor)
                && !reachableFrom(
                        possibleAncestor,
                        objectsBySubject,
                        ancestorCache).contains(possibleDescendant);
    }

    private Set<String> reachableFrom(
            String start,
            Map<String, Set<String>> objectsBySubject,
            Map<String, Set<String>> ancestorCache
    ) {
        Set<String> cached = ancestorCache.get(start);
        if (cached != null) {
            return cached;
        }

        Deque<String> frontier = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        frontier.addAll(objectsBySubject.getOrDefault(start, Set.of()));

        while (!frontier.isEmpty()) {
            String current = frontier.removeFirst();
            if (!visited.add(current)) {
                continue;
            }

            for (String object : objectsBySubject
                    .getOrDefault(current, Set.of())) {
                frontier.addLast(object);
            }
        }

        Set<String> reachable = Set.copyOf(visited);
        ancestorCache.put(start, reachable);
        return reachable;
    }
}
