package cz.cvut.fel.ontodiff.statesearch;

import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import org.semanticweb.owlapi.model.OWLAxiom;

import java.util.*;

//public class StateSpaceSearch {

//    private List<ChangeCandidate> findApplicableCandidates(SearchState state, List<ChangeCandidate> candidates) {
//        return candidates.stream().filter(state::canApply).toList();
//    }
//
//    private List<SearchState> createChildStates(SearchState state, List<ChangeCandidate> applicableCandidates) {
//        return applicableCandidates.stream().map(state::apply).toList();
//    }
//
//    public SearchState search(DiffResult diff, List<ChangeCandidate> candidates) {
//
////        ensureAllDifferencesHaveCandidate(diff, candidates);
//
//        CandidateIndex candidateIndex = new CandidateIndex(candidates);
//
//        SearchState initialState = initialSupportedState(diff, candidates);
//
//        PriorityQueue<SearchState> frontier = new PriorityQueue<>(Comparator.comparingInt(SearchState::cost));
//
//        Map<StateKey, Integer> bestKnownCost = new HashMap<>();
//
//        StateKey initialKey = StateKey.from(initialState);
//
//        bestKnownCost.put(initialKey, initialState.cost());
//
//        frontier.add(initialState);
//
//        while (!frontier.isEmpty()) {
//            SearchState currentState = frontier.poll();
//
//            StateKey currentKey = StateKey.from(currentState);
//
//            int knownCost = bestKnownCost.getOrDefault(currentKey, Integer.MAX_VALUE);
//
//            // if cheaper version of this state was already  found
//            if (currentState.cost() > knownCost) {
//                continue;
//            }
//
//            if (currentState.isGoal()) {
//                return currentState;
//            }
//
//            List<ChangeCandidate> applicableCandidates = candidateIndex.findApplicable(currentState);
//
//            List<SearchState> childStates = createChildStates(currentState, applicableCandidates);
//
//            for (SearchState childState : childStates) {
//                StateKey childKey = StateKey.from(childState);
//
//                int previousCost = bestKnownCost.getOrDefault(childKey, Integer.MAX_VALUE);
//
//                if (childState.cost() < previousCost) {
//                    bestKnownCost.put(childKey, childState.cost());
//                    frontier.add(childState);
//                }
//            }
//        }
//
//        throw new IllegalStateException("No combination of candidates explains all differences");
//    }
//
//    private SearchState initialSupportedState(DiffResult diff, List<ChangeCandidate> candidates) {
//        Set<OWLAxiom> allRemoved = new HashSet<>(diff.getOnlyInOriginal());
//
//        Set<OWLAxiom> allAdded = new HashSet<>(diff.getOnlyInUpdate());
//
//        Set<OWLAxiom> coveredRemoved = new HashSet<>();
//
//        Set<OWLAxiom> coveredAdded = new HashSet<>();
//
//        for (ChangeCandidate candidate : candidates) {
//            if (!allRemoved.containsAll(candidate.removedEvidence()) || !allAdded.containsAll(candidate.addedEvidence())) {
//                throw new IllegalArgumentException("Candidate contains evidence outside DiffResult: " + candidate);
//            }
//
//            coveredRemoved.addAll(candidate.removedEvidence());
//            coveredAdded.addAll(candidate.addedEvidence());
//        }
//
//        return new SearchState(coveredRemoved, coveredAdded, List.of(), 0);
//    }
//
//    private void ensureAllDifferencesHaveCandidate(DiffResult diff, List<ChangeCandidate> candidates) {
//        Set<OWLAxiom> uncoveredRemove = new HashSet<>(diff.getOnlyInOriginal());
//
//        Set<OWLAxiom> uncoveredAdd = new HashSet<>(diff.getOnlyInUpdate());
//
//        for (ChangeCandidate candidate : candidates) {
//            uncoveredRemove.removeAll(candidate.removedEvidence());
//            uncoveredAdd.removeAll(candidate.addedEvidence());
//        }
//
//        if (!uncoveredRemove.isEmpty() || !uncoveredAdd.isEmpty()) {
//            System.out.println(uncoveredRemove);
//            throw new IllegalStateException("Search cannot reach a goal state. " + "Uncovered removed axioms: "
//                    + uncoveredRemove.size() + ", uncovered added axioms: " + uncoveredAdd.size()
//            );
//        }
//    }
//}

//package cz.cvut.fel.ontodiff.statesearch;

public class StateSpaceSearch {

    private record SearchScore(int cost, double confidencePenalty)
            implements Comparable<SearchScore> {

        static SearchScore from(SearchState state) {
            return new SearchScore(state.cost(), state.confidencePenalty());
        }

        @Override
        public int compareTo(SearchScore other) {
            int costComparison = Integer.compare(cost, other.cost);
            return costComparison != 0
                    ? costComparison
                    : Double.compare(confidencePenalty, other.confidencePenalty);
        }
    }

    public SearchState search(
            DiffResult diff,
            List<ChangeCandidate> candidates
    ) {
        validateEvidence(diff, candidates);

        List<List<ChangeCandidate>> groups = splitBySharedEvidence(candidates);

        int largestGroup = groups.stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        System.out.println(
                "Search groups: " + groups.size()
                        + ", largest group: " + largestGroup + " candidates"
        );

        List<ChangeCandidate> selected = new ArrayList<>();
        int totalCost = 0;

        for (List<ChangeCandidate> group : groups) {
            SearchState groupResult = searchGroup(group);

            selected.addAll(groupResult.selectedCandidates());
            totalCost += groupResult.cost();
        }

        // All evidence supported by candidates was handled in its own group.
        // Unsupported axioms are still handled by FallbackChanges afterward.
        return new SearchState(
                Set.of(),
                Set.of(),
                selected,
                totalCost
        );
    }

    private SearchState searchGroup(List<ChangeCandidate> group) {
        Set<OWLAxiom> removed = new HashSet<>();
        Set<OWLAxiom> added = new HashSet<>();

        for (ChangeCandidate candidate : group) {
            removed.addAll(candidate.removedEvidence());
            added.addAll(candidate.addedEvidence());
        }

        SearchState initial = new SearchState(
                removed,
                added,
                List.of(),
                0
        );

        CandidateIndex index = new CandidateIndex(group);

        PriorityQueue<SearchState> frontier =
                new PriorityQueue<>(
                        Comparator.comparing(SearchScore::from)
                );

        Map<StateKey, SearchScore> bestKnownScore = new HashMap<>();

        frontier.add(initial);
        bestKnownScore.put(StateKey.from(initial), SearchScore.from(initial));

        while (!frontier.isEmpty()) {
            SearchState current = frontier.poll();
            StateKey currentKey = StateKey.from(current);
            SearchScore currentScore = SearchScore.from(current);

            if (currentScore.compareTo(bestKnownScore.get(currentKey)) > 0) {
                continue;
            }

            if (current.isGoal()) {
                return current;
            }

            for (ChangeCandidate candidate : index.findApplicable(current)) {
                SearchState child = current.apply(candidate);
                StateKey childKey = StateKey.from(child);
                SearchScore childScore = SearchScore.from(child);

                SearchScore previousScore = bestKnownScore.get(childKey);

                if (previousScore == null || childScore.compareTo(previousScore) < 0) {
                    bestKnownScore.put(childKey, childScore);
                    frontier.add(child);
                }
            }
        }

        throw new IllegalStateException(
                "No combination covers one search group of "
                        + group.size() + " candidates"
        );
    }

    private List<List<ChangeCandidate>> splitBySharedEvidence(
            List<ChangeCandidate> candidates
    ) {
        int count = candidates.size();
        int[] parent = new int[count];
        int[] size = new int[count];

        for (int i = 0; i < count; i++) {
            parent[i] = i;
            size[i] = 1;
        }

        Map<OWLAxiom, Integer> removedOwner = new HashMap<>();
        Map<OWLAxiom, Integer> addedOwner = new HashMap<>();

        for (int i = 0; i < count; i++) {
            ChangeCandidate candidate = candidates.get(i);

            if (candidate.removedEvidence().isEmpty()
                    && candidate.addedEvidence().isEmpty()) {
                throw new IllegalArgumentException(
                        "Candidate has no evidence: " + candidate
                );
            }

            for (OWLAxiom axiom : candidate.removedEvidence()) {
                Integer previous = removedOwner.putIfAbsent(axiom, i);
                if (previous != null) {
                    union(parent, size, i, previous);
                }
            }

            for (OWLAxiom axiom : candidate.addedEvidence()) {
                Integer previous = addedOwner.putIfAbsent(axiom, i);
                if (previous != null) {
                    union(parent, size, i, previous);
                }
            }
        }

        Map<Integer, List<ChangeCandidate>> groups =
                new LinkedHashMap<>();

        for (int i = 0; i < count; i++) {
            int root = find(parent, i);
            groups.computeIfAbsent(root, ignored -> new ArrayList<>())
                    .add(candidates.get(i));
        }

        return new ArrayList<>(groups.values());
    }

    private int find(int[] parent, int index) {
        while (parent[index] != index) {
            parent[index] = parent[parent[index]];
            index = parent[index];
        }
        return index;
    }

    private void union(int[] parent, int[] size, int first, int second) {
        int firstRoot = find(parent, first);
        int secondRoot = find(parent, second);

        if (firstRoot == secondRoot) {
            return;
        }

        if (size[firstRoot] < size[secondRoot]) {
            int temporary = firstRoot;
            firstRoot = secondRoot;
            secondRoot = temporary;
        }

        parent[secondRoot] = firstRoot;
        size[firstRoot] += size[secondRoot];
    }

    private void validateEvidence(DiffResult diff, List<ChangeCandidate> candidates) {
        Set<OWLAxiom> removed = new HashSet<>(diff.getOnlyInOriginal());
        Set<OWLAxiom> added = new HashSet<>(diff.getOnlyInUpdate());

        for (ChangeCandidate candidate : candidates) {
            if (!removed.containsAll(candidate.removedEvidence()) || !added.containsAll(candidate.addedEvidence())) {
                throw new IllegalArgumentException("Candidate contains evidence outside DiffResult: " + candidate);
            }
        }
    }
}
