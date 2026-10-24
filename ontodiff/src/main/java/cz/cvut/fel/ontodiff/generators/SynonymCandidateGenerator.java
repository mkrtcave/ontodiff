package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.properties.SynonymPropety;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class SynonymCandidateGenerator implements CandidateGenerator {

    private record SynonymFact(
            IRI entityIri,
            IRI propertyIri,
            OWLLiteral literal,
            OWLAnnotationAssertionAxiom sourceAxiom
    ) {
        SynonymIdentity identity() {
            return new SynonymIdentity(entityIri, propertyIri, literal);
        }

        PairKey pairKey() {
            return new PairKey(entityIri, propertyIri,
                    literal.getLang(), literal.getDatatype().getIRI());
        }
    }

    private record SynonymIdentity(IRI entityIri, IRI propertyIri, OWLLiteral literal) {}

    private record PairKey(IRI entityIri, IRI propertyIri, String language, IRI datatypeIri) {}

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        List<SynonymFact> removedFacts = extract(diff.getOnlyInOriginal());
        List<SynonymFact> addedFacts = extract(diff.getOnlyInUpdate());

        Set<SynonymIdentity> removedIdentities = new HashSet<>();
        Set<SynonymIdentity> addedIdentities = new HashSet<>();
        removedFacts.forEach(fact -> removedIdentities.add(fact.identity()));
        addedFacts.forEach(fact -> addedIdentities.add(fact.identity()));

        // A literal present on both sides may only have changed axiom annotations.
        // Do not call that a synonym addition, removal, or replacement.
        List<SynonymFact> removed = removedFacts.stream()
                .filter(fact -> !addedIdentities.contains(fact.identity()))
                .toList();
        List<SynonymFact> added = addedFacts.stream()
                .filter(fact -> !removedIdentities.contains(fact.identity()))
                .toList();

        List<ChangeCandidate> candidates = new ArrayList<>();
        removed.forEach(fact -> candidates.add(removal(fact)));
        added.forEach(fact -> candidates.add(addition(fact)));

        Map<PairKey, List<SynonymFact>> removedByKey = groupByPairKey(removed);
        Map<PairKey, List<SynonymFact>> addedByKey = groupByPairKey(added);

        for (Map.Entry<PairKey, List<SynonymFact>> entry : removedByKey.entrySet()) {
            List<SynonymFact> oldSynonyms = entry.getValue();
            List<SynonymFact> newSynonyms = addedByKey.getOrDefault(entry.getKey(), List.of());
            if (oldSynonyms.size() != 1 || newSynonyms.size() != 1
                    || classCreatedOrDeleted(entry.getKey().entityIri(), diff)) {
                continue;
            }

            SynonymFact oldFact = oldSynonyms.get(0);
            SynonymFact newFact = newSynonyms.get(0);
            candidates.add(new ChangeCandidate(
                    new HighLevelDiffService.SynonymReplacement(
                            oldFact.entityIri().toString(),
                            oldFact.propertyIri().toString(),
                            display(oldFact.literal()),
                            display(newFact.literal())),
                    Set.of(oldFact.sourceAxiom()),
                    Set.of(newFact.sourceAxiom()),
                    CandidateConfidence.WEAK_INTERPRETATION,
                    1,
                    "same-entity-synonym-property-language-datatype"
            ));
        }

        return candidates.stream();
    }

    private List<SynonymFact> extract(List<OWLAxiom> axioms) {
        return axioms.stream().map(this::fact).flatMap(Optional::stream).toList();
    }

    private Optional<SynonymFact> fact(OWLAxiom axiom) {
        if (!(axiom instanceof OWLAnnotationAssertionAxiom annotation)
                || !SynonymPropety.SYNONYM_PROPERTY_IRIS.contains(
                        annotation.getProperty().getIRI().toString())
                || !(annotation.getSubject() instanceof IRI entityIri)
                || !(annotation.getValue() instanceof OWLLiteral literal)) {
            return Optional.empty();
        }

        return Optional.of(new SynonymFact(
                entityIri,
                annotation.getProperty().getIRI(),
                literal,
                annotation));
    }

    private Map<PairKey, List<SynonymFact>> groupByPairKey(List<SynonymFact> facts) {
        Map<PairKey, List<SynonymFact>> result = new HashMap<>();
        for (SynonymFact fact : facts) {
            result.computeIfAbsent(fact.pairKey(), ignored -> new ArrayList<>()).add(fact);
        }
        return result;
    }

    private boolean classCreatedOrDeleted(IRI entityIri, DiffResult diff) {
        boolean inOriginal = diff.getOriginal()
                .containsClassInSignature(entityIri, Imports.INCLUDED);
        boolean inUpdate = diff.getUpdate()
                .containsClassInSignature(entityIri, Imports.INCLUDED);
        return inOriginal != inUpdate;
    }

    private ChangeCandidate addition(SynonymFact fact) {
        return new ChangeCandidate(
                new HighLevelDiffService.NewSynonym(
                        fact.entityIri().toString(),
                        fact.propertyIri().toString(),
                        display(fact.literal())),
                Set.of(),
                Set.of(fact.sourceAxiom()),
                CandidateConfidence.DIRECT,
                1,
                "synonym-added"
        );
    }

    private ChangeCandidate removal(SynonymFact fact) {
        return new ChangeCandidate(
                new HighLevelDiffService.RemoveSynonym(
                        fact.entityIri().toString(),
                        fact.propertyIri().toString(),
                        display(fact.literal())),
                Set.of(fact.sourceAxiom()),
                Set.of(),
                CandidateConfidence.DIRECT,
                1,
                "synonym-removed"
        );
    }

    private String display(OWLLiteral literal) {
        String text = literal.getLiteral();
        if (literal.hasLang()) {
            return text + "@" + literal.getLang();
        }
        if (!literal.isRDFPlainLiteral()) {
            return text + "^^" + literal.getDatatype().getIRI();
        }
        return text;
    }
}
