package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.util.OWLObjectDuplicator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IriRenameCandidateGenerator implements CandidateGenerator {

    private record RenameMatch(IRI oldIri, IRI newIri, Set<OWLAxiom> removedEvidence, Set<OWLAxiom> addedEvidence) {}

    @Override
    public Stream<ChangeCandidate> generate(DiffResult diff) {
        Map<IRI, Set<EntityType<?>>> removedDeclarations = declarationProfiles(diff.getOnlyInOriginal());
        Map<IRI, Set<EntityType<?>>> addedDeclarations = declarationProfiles(diff.getOnlyInUpdate());

        Set<IRI> originalIris = signatureIris(diff, true);
        Set<IRI> updatedIris = signatureIris(diff, false);
        Map<IRI, Set<OWLAxiom>> removedAxiomsByIri = indexAxioms(diff.getOnlyInOriginal(), removedDeclarations.keySet());
        Map<IRI, Set<OWLAxiom>> addedAxiomsByIri = indexAxioms(diff.getOnlyInUpdate(), addedDeclarations.keySet());
        Map<OWLObject, Set<IRI>> addedIrisByTerm = indexStableTerms(addedAxiomsByIri);
        List<RenameMatch> matches = findMatches(
                diff,
                removedDeclarations,
                addedDeclarations,
                originalIris,
                updatedIris,
                removedAxiomsByIri,
                addedAxiomsByIri,
                addedIrisByTerm);

        Map<IRI, Long> matchesByOldIri = matches.stream().collect(Collectors.groupingBy(
                RenameMatch::oldIri, LinkedHashMap::new, Collectors.counting()));
        Map<IRI, Long> matchesByNewIri = matches.stream().collect(Collectors.groupingBy(
                RenameMatch::newIri, LinkedHashMap::new, Collectors.counting()));

        return matches.stream()
                .filter(match -> matchesByOldIri.get(match.oldIri()) == 1)
                .filter(match -> matchesByNewIri.get(match.newIri()) == 1)
                .map(this::createCandidate);
    }

    private Map<IRI, Set<EntityType<?>>> declarationProfiles(List<OWLAxiom> axioms) {
        Map<IRI, Set<EntityType<?>>> result = new LinkedHashMap<>();

        axioms.stream()
                .filter(OWLDeclarationAxiom.class::isInstance)
                .map(OWLDeclarationAxiom.class::cast)
                .forEach(declaration -> result.computeIfAbsent(
                                declaration.getEntity().getIRI(),
                                ignored -> new HashSet<>())
                        .add(declaration.getEntity().getEntityType()));

        return result;
    }

    private Set<IRI> signatureIris(DiffResult diff, boolean original) {
        return (original ? diff.getOriginal() : diff.getUpdate())
                .getSignature(Imports.INCLUDED)
                .stream()
                .map(OWLEntity::getIRI)
                .collect(Collectors.toSet());
    }

    private Map<IRI, Set<OWLAxiom>> indexAxioms(List<OWLAxiom> axioms, Set<IRI> candidateIris) {
        Map<IRI, Set<OWLAxiom>> index = new LinkedHashMap<>();
        for (OWLAxiom axiom : axioms) {
            Set<OWLObject> terms = new HashSet<>();
            collectTerms(axiom, terms);
            for (OWLObject term : terms) {
                if (term instanceof IRI iri && candidateIris.contains(iri)) {
                    index.computeIfAbsent(iri, ignored -> new HashSet<>()).add(axiom);
                }
            }
        }
        return index;
    }

    private Map<OWLObject, Set<IRI>> indexStableTerms(Map<IRI, Set<OWLAxiom>> axiomsByIri) {
        Map<OWLObject, Set<IRI>> index = new LinkedHashMap<>();
        for (Map.Entry<IRI, Set<OWLAxiom>> entry : axiomsByIri.entrySet()) {
            for (OWLObject term : stableTerms(entry.getValue(), entry.getKey())) {
                index.computeIfAbsent(term, ignored -> new HashSet<>()).add(entry.getKey());
            }
        }
        return index;
    }

    private Set<OWLObject> stableTerms(Set<OWLAxiom> axioms, IRI changingIri) {
        Set<OWLObject> terms = new HashSet<>();
        axioms.forEach(axiom -> collectTerms(axiom, terms));
        terms.remove(changingIri);
        return terms;
    }

    private void collectTerms(Object object, Set<OWLObject> result) {
        if (object instanceof IRI iri) {
            result.add(iri);
        } else if (object instanceof OWLLiteral literal) {
            result.add(literal);
        } else if (object instanceof OWLEntity entity) {
            result.add(entity.getIRI());
        } else if (object instanceof OWLObject owlObject) {
            owlObject.components().forEach(component -> collectTerms(component, result));
        } else if (object instanceof Iterable<?> values) {
            values.forEach(value -> collectTerms(value, result));
        }
    }

    private List<RenameMatch> findMatches(
            DiffResult diff,
            Map<IRI, Set<EntityType<?>>> removedDeclarations,
            Map<IRI, Set<EntityType<?>>> addedDeclarations,
            Set<IRI> originalIris,
            Set<IRI> updatedIris,
            Map<IRI, Set<OWLAxiom>> removedAxiomsByIri,
            Map<IRI, Set<OWLAxiom>> addedAxiomsByIri,
            Map<OWLObject, Set<IRI>> addedIrisByTerm
    ) {
        List<RenameMatch> matches = new ArrayList<>();

        for (Map.Entry<IRI, Set<EntityType<?>>> removed : removedDeclarations.entrySet()) {
            if (updatedIris.contains(removed.getKey())) {
                continue;
            }

            Set<OWLAxiom> removedEvidence = removedAxiomsByIri.getOrDefault(
                    removed.getKey(), Set.of());
            if (removedEvidence.stream().noneMatch(axiom -> !(axiom instanceof OWLDeclarationAxiom))) {
                continue;
            }

            Set<IRI> possibleNewIris = possibleNewIris(
                    removedEvidence, removed.getKey(), addedDeclarations.keySet(), addedIrisByTerm);
            for (IRI newIri : possibleNewIris) {
                Set<EntityType<?>> addedTypes = addedDeclarations.get(newIri);
                if (originalIris.contains(newIri)
                        || !removed.getValue().equals(addedTypes)) {
                    continue;
                }

                Set<OWLAxiom> addedEvidence = addedAxiomsByIri.getOrDefault(
                        newIri, Set.of());
                if (removedEvidence.size() != addedEvidence.size()) {
                    continue;
                }

                exactMatch(diff, removed.getKey(), newIri,
                        removedEvidence, addedEvidence).ifPresent(matches::add);
            }
        }

        return matches;
    }

    private Set<IRI> possibleNewIris(Set<OWLAxiom> removedEvidence, IRI oldIri, Set<IRI> allAddedIris,
            Map<OWLObject, Set<IRI>> addedIrisByTerm) {
        Set<IRI> smallest = allAddedIris;
        for (OWLObject term : stableTerms(removedEvidence, oldIri)) {
            Set<IRI> matching = addedIrisByTerm.getOrDefault(term, Set.of());
            if (matching.size() < smallest.size()) {
                smallest = matching;
            }
        }
        return smallest;
    }

    private Optional<RenameMatch> exactMatch(
            DiffResult diff,
            IRI oldIri,
            IRI newIri,
            Set<OWLAxiom> removedEvidence,
            Set<OWLAxiom> addedEvidence
    ) {
        OWLObjectDuplicator forward = duplicator(
                diff.getOriginal().getOWLOntologyManager(), oldIri, newIri);
        OWLObjectDuplicator reverse = duplicator(
                diff.getUpdate().getOWLOntologyManager(), newIri, oldIri);
        if (removedEvidence.isEmpty()
                || addedEvidence.isEmpty()
                || removedEvidence.stream().allMatch(OWLDeclarationAxiom.class::isInstance)
                || addedEvidence.stream().allMatch(OWLDeclarationAxiom.class::isInstance)) {
            return Optional.empty();
        }

        for (OWLAxiom axiom : removedEvidence) {
            if (!addedEvidence.contains(forward.duplicateObject(axiom))) {
                return Optional.empty();
            }
        }
        for (OWLAxiom axiom : addedEvidence) {
            if (!removedEvidence.contains(reverse.duplicateObject(axiom))) {
                return Optional.empty();
            }
        }

        return Optional.of(new RenameMatch(
                oldIri,
                newIri,
                removedEvidence,
                addedEvidence));
    }

    private OWLObjectDuplicator duplicator(OWLOntologyManager manager, IRI source, IRI target) {
        return new OWLObjectDuplicator(manager, Map.of(source, target));
    }

    private ChangeCandidate createCandidate(RenameMatch match) {
        return new ChangeCandidate(
                new HighLevelDiffService.IriRename(
                        match.oldIri().toString(),
                        match.newIri().toString()),
                match.removedEvidence(),
                match.addedEvidence(),
                CandidateConfidence.UNAMBIGUOUS_PAIR,
                1,
                "exact-full-iri-substitution-with-matching-axiom-neighbourhood"
        );
    }
}
