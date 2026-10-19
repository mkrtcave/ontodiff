package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class EquivalentClassesChangeCandidateGenerator implements CandidateGenerator {

    private record DefinitionFact(
            IRI classIri,
            OWLClassExpression expression,
            OWLEquivalentClassesAxiom sourceAxiom
    ) {}

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        Map<IRI, List<DefinitionFact>> removed = byDefinedClass(diff.getOnlyInOriginal());
        Map<IRI, List<DefinitionFact>> added = byDefinedClass(diff.getOnlyInUpdate());
        List<ChangeCandidate> candidates = new ArrayList<>();

        for (Map.Entry<IRI, List<DefinitionFact>> entry : removed.entrySet()) {
            List<DefinitionFact> oldDefinitions = entry.getValue();
            List<DefinitionFact> newDefinitions = added.getOrDefault(entry.getKey(), List.of());

            if (oldDefinitions.size() != 1 || newDefinitions.size() != 1) {
                continue;
            }

            DefinitionFact oldFact = oldDefinitions.get(0);
            DefinitionFact newFact = newDefinitions.get(0);
            if (oldFact.expression().equals(newFact.expression())) {
                continue;
            }

            candidates.add(new ChangeCandidate(
                    new HighLevelDiffService.EquivalentClassesChange(
                            entry.getKey().toString(),
                            oldFact.expression(),
                            newFact.expression()
                    ),
                    Set.of(oldFact.sourceAxiom()),
                    Set.of(newFact.sourceAxiom()),
                    CandidateConfidence.UNAMBIGUOUS_PAIR,
                    1,
                    "same-class-different-equivalent-expression"
            ));
        }

        return candidates.stream();
    }

    private Map<IRI, List<DefinitionFact>> byDefinedClass(List<OWLAxiom> axioms) {
        Map<IRI, List<DefinitionFact>> result = new HashMap<>();

        for (OWLAxiom axiom : axioms) {
            definitionFact(axiom).ifPresent(fact ->
                    result.computeIfAbsent(fact.classIri(), ignored -> new ArrayList<>())
                            .add(fact));
        }

        return result;
    }

    private Optional<DefinitionFact> definitionFact(OWLAxiom axiom) {
        if (!(axiom instanceof OWLEquivalentClassesAxiom equivalent)) {
            return Optional.empty();
        }

        List<OWLClassExpression> expressions = equivalent.classExpressions().toList();
        if (expressions.size() != 2) {
            return Optional.empty();
        }

        List<OWLClassExpression> named = expressions.stream()
                .filter(expression -> !expression.isAnonymous())
                .toList();
        List<OWLClassExpression> anonymous = expressions.stream()
                .filter(OWLClassExpression::isAnonymous)
                .toList();

        if (named.size() != 1 || anonymous.size() != 1) {
            return Optional.empty();
        }

        return Optional.of(new DefinitionFact(
                named.get(0).asOWLClass().getIRI(),
                anonymous.get(0),
                equivalent
        ));
    }
}
