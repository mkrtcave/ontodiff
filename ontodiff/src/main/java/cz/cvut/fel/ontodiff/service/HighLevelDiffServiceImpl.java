package cz.cvut.fel.ontodiff.service;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.EdgeFact;
import cz.cvut.fel.ontodiff.properties.SynonymPropety;
import cz.cvut.fel.ontodiff.properties.TextDefinitionProperties;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.util.*;
import java.util.stream.Collectors;


public class HighLevelDiffServiceImpl implements HighLevelDiffService{

    private static final String SUBCLASS_PROP = "rdfs:subClassOf";

    public Set<NodeCreation> computeNodeCreations(DiffResult diff) {
        OWLOntology update = diff.getUpdate();
        OWLOntology original = diff.getOriginal();

        Set<OWLEntity> originalEntities = original.getSignature();
        Set<OWLEntity> updateEntities = update.getSignature();
        Set<OWLEntity> created = new HashSet<>(updateEntities);
        created.removeAll(originalEntities);

        return created.stream()
                .map(e -> new NodeCreation(e.getIRI().getIRIString()))
                .collect(Collectors.toSet());
    }

    private Set<NodeDeletion> computeNodeDeletions(DiffResult diff) {
        OWLOntology update = diff.getUpdate();
        OWLOntology original = diff.getOriginal();

        Set<OWLEntity> originalEntities = original.getSignature();
        Set<OWLEntity> updateEntities = update.getSignature();
        Set<OWLEntity> deleted = new HashSet<>(originalEntities);
        deleted.removeAll(updateEntities);

        return deleted.stream()
                .map(e -> new NodeDeletion(e.getIRI().toString()))
                .collect(Collectors.toSet());
    }

    private Set<NodeObsoletion> computeNodeObsoletions(DiffResult diff) {
        OWLOntology original = diff.getOriginal();
        OWLOntology update = diff.getUpdate();

        Set<OWLEntity> originalEntities = original.getSignature();
        Set<OWLEntity> updateEntities = update.getSignature();

        Set<OWLEntity> common = new HashSet<>(originalEntities);
        common.retainAll(updateEntities);

        Set<NodeObsoletion> result = new HashSet<>();

        for (OWLEntity e : common) {
            boolean wasObsolete = isObsolete(e, original);
            boolean isObsoleteNow = isObsolete(e, update);

            if (!wasObsolete && isObsoleteNow) {
                String reason = "deprecated-flag-added";
                result.add(new NodeObsoletion(e.getIRI().toString(), reason));
            }
        }

        return result;
    }



    private boolean isObsolete(OWLEntity entity, OWLOntology ont) {
        IRI iri = entity.getIRI();

        for (OWLOntology o : ont.getImportsClosure()) {
            for (OWLAnnotationAssertionAxiom ax : o.getAnnotationAssertionAxioms(iri)) {

                OWLAnnotationProperty prop = ax.getProperty();
                OWLAnnotationValue val = ax.getValue();

                boolean isDeprecatedProp = prop.getIRI().equals(OWLRDFVocabulary.OWL_DEPRECATED.getIRI());

                if (isDeprecatedProp && val instanceof OWLLiteral lit) {
                    if (lit.isBoolean() && lit.parseBoolean()) {
                        return true;
                    }
                }

                IRI propIri = prop.getIRI();
                if (propIri != null &&
                        propIri.toString().endsWith("is_obsolete") &&
                        val instanceof OWLLiteral lit2 &&
                        "true".equalsIgnoreCase(lit2.getLiteral())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Set<ClassCreation> computeClassCreations(DiffResult diff){
        OWLOntology update = diff.getUpdate();
        OWLOntology original = diff.getOriginal();

        Set<OWLClass> oldClasses = original.getClassesInSignature();
        Set<OWLClass> newClasses = update.getClassesInSignature();

        Set<OWLClass> created = new HashSet<>(newClasses);
        created.removeAll(oldClasses);

        return created.stream()
                .map(c -> new ClassCreation(c.getIRI().toString()))
                .collect(Collectors.toSet());
    }

    public Set<SynonymReplacement> computeSynonymReplacements(DiffResult diff) {

        Map<String, Map<String, Set<OWLLiteral>>> removedSyns =
                collectSynonymLiterals(diff.getOnlyInOriginal());
        Map<String, Map<String, Set<OWLLiteral>>> addedSyns =
                collectSynonymLiterals(diff.getOnlyInUpdate());

        Set<SynonymReplacement> result = new HashSet<>();

        for (String entityIri : removedSyns.keySet()) {
            Map<String, Set<OWLLiteral>> removedByProp = removedSyns.get(entityIri);
            Map<String, Set<OWLLiteral>> addedByProp   =
                    addedSyns.getOrDefault(entityIri, Map.of());

            for (String propIri : removedByProp.keySet()) {
                Set<OWLLiteral> removedVals = removedByProp.get(propIri);
                Set<OWLLiteral> addedVals   = addedByProp.getOrDefault(propIri, Set.of());

                for (OWLLiteral oldLit : removedVals) {
                    for (OWLLiteral newLit : addedVals) {
                        String oldS = literalToDisplay(oldLit);
                        String newS = literalToDisplay(newLit);
                        if (oldS.equals(newS)) {
                            continue;
                        }
                        result.add(new SynonymReplacement(
                                entityIri,
                                propIri,
                                oldS,
                                newS
                        ));
                    }
                }
            }
        }
        return result;
    }

    private Map<String, Map<String, Set<OWLLiteral>>> collectSynonymLiterals(Collection<OWLAxiom> axioms) {
        Map<String, Map<String, Set<OWLLiteral>>> result = new HashMap<>();

        for (OWLAxiom ax : axioms) {
            if (!(ax instanceof OWLAnnotationAssertionAxiom annAx)) continue;
            OWLAnnotationProperty prop = annAx.getProperty();
            if (!isSynonymProperty(prop)) continue;
            if (!(annAx.getSubject() instanceof IRI entityIri)) continue;
            if (!(annAx.getValue() instanceof OWLLiteral lit)) continue;

            String ent = entityIri.toString();
            String prp = prop.getIRI().toString();

            result.computeIfAbsent(ent, e -> new HashMap<>())
                    .computeIfAbsent(prp, p -> new HashSet<>())
                    .add(lit);
        }

        return result;
    }

    private String literalToDisplay(OWLLiteral lit) {
        String s = lit.getLiteral();
        if (lit.hasLang()) {
            s = s + "@" + lit.getLang();
        } else if (!lit.isRDFPlainLiteral() && lit.getDatatype() != null) {
            s = s + "^^" + lit.getDatatype().getIRI();
        }
        return s;
    }

    private static boolean isSynonymProperty(OWLAnnotationProperty prop) {
        String iri = prop.getIRI().toString();
        return SynonymPropety.SYNONYM_PROPERTY_IRIS.contains(iri);
    }

    private Map<String, Map<String, Set<String>>> collectSynonyms( Collection<OWLAxiom> axioms) {
        Map<String, Map<String, Set<String>>> result = new HashMap<>();

        for (OWLAxiom ax : axioms) {
            if (!(ax instanceof OWLAnnotationAssertionAxiom annAx)) {
                continue;
            }
            OWLAnnotationProperty prop = annAx.getProperty();
            if (!isSynonymProperty(prop)) {
                continue;
            }
            if (!(annAx.getSubject() instanceof IRI subjectIri)) {
                continue;
            }
            if (!(annAx.getValue() instanceof OWLLiteral lit)) {
                continue;
            }
            String entityIri = subjectIri.toString();
            String propertyIri = prop.getIRI().toString();
            String synonym = lit.getLiteral();

            result.computeIfAbsent(entityIri, e -> new HashMap<>())
                    .computeIfAbsent(propertyIri, p -> new HashSet<>())
                    .add(synonym);
        }

        return result;
    }

    private Set<EdgeCreation> extractEdgesFromAxiom(OWLAxiom axiom) {
        Set<EdgeCreation> result = new HashSet<>();
        if (axiom instanceof OWLSubClassOfAxiom sc) {
            if (sc.getSubClass().isNamed() && sc.getSuperClass().isNamed()) {
                OWLClass sub = sc.getSubClass().asOWLClass();
                OWLClass sup = sc.getSuperClass().asOWLClass();
                result.add(new EdgeCreation(
                        sub.getIRI().toString(),
                        SUBCLASS_PROP,
                        sup.getIRI().toString()
                ));
            }

        } else if (axiom instanceof OWLObjectPropertyDomainAxiom dom) {
            if (dom.getDomain().isNamed()) {
                OWLObjectPropertyExpression prop = dom.getProperty();
                OWLClass domain = dom.getDomain().asOWLClass();
                result.add(new EdgeCreation(
                        prop.asOWLObjectProperty().getIRI().toString(),
                        "domainOf",
                        domain.getIRI().toString()
                ));
            }

        } else if (axiom instanceof OWLObjectPropertyRangeAxiom range) {
            if (range.getRange().isNamed()) {
                OWLObjectPropertyExpression prop = range.getProperty();
                OWLClass rangeCls = range.getRange().asOWLClass();
                result.add(new EdgeCreation(
                        prop.asOWLObjectProperty().getIRI().toString(),
                        "rangeOf",
                        rangeCls.getIRI().toString()
                ));
            }

        } else if (axiom instanceof OWLObjectPropertyAssertionAxiom pa) {
            if (!pa.getSubject().isAnonymous() && !pa.getObject().isAnonymous()) {
                OWLIndividual subj = pa.getSubject().asOWLNamedIndividual();
                OWLIndividual obj  = pa.getObject().asOWLNamedIndividual();
                OWLObjectPropertyExpression prop = pa.getProperty();
                result.add(new EdgeCreation(
                        subj.asOWLNamedIndividual().getIRI().toString(),
                        prop.asOWLObjectProperty().getIRI().toString(),
                        obj.asOWLNamedIndividual().getIRI().toString()
                ));
            }

        } else if (axiom instanceof OWLAnnotationAssertionAxiom annAx) {
            if (!(annAx.getSubject() instanceof IRI subjIri)) {
                return result;
            }
            if (!(annAx.getValue() instanceof IRI objIri)) {
                return result;
            }
            OWLAnnotationProperty prop = annAx.getProperty();
            result.add(new EdgeCreation(
                    subjIri.toString(),
                    prop.getIRI().toString(),
                    objIri.toString()
            ));
        }

        return result;
    }

    private Optional<EdgeFact> extractSimpleEdge(OWLAxiom axiom) {
        if (axiom instanceof OWLSubClassOfAxiom subClassAxiom
                && !subClassAxiom.getSubClass().isAnonymous()
                && !subClassAxiom.getSuperClass().isAnonymous()) {

            return Optional.of(new EdgeFact(
                    subClassAxiom.getSubClass()
                            .asOWLClass()
                            .getIRI()
                            .toString(),
                    SUBCLASS_PROP,
                    subClassAxiom.getSuperClass()
                            .asOWLClass()
                            .getIRI()
                            .toString(),
                    axiom
            ));
        }

        return Optional.empty();
    }

    private Set<EdgeCreation> computeEdgeCreations(DiffResult diff) {
        return diff.getOnlyInUpdate().stream()
                .map(this::extractEdgesFromAxiom)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    private Set<EdgeDeletion> computeEdgeDeletions(DiffResult diff) {
        return diff.getOnlyInOriginal().stream()
                .map(this::extractEdgesFromAxiom)
                .flatMap(Set::stream)
                .map(e -> new EdgeDeletion(e.srcIri(), e.propIri(), e.tgtIri()))
                .collect(Collectors.toSet());
    }

    private Set<NodeMove> computeNodeMoves(Set<EdgeCreation> edgeCreations,
                                           Set<EdgeDeletion> edgeDeletions) {
        final String property = "rdfs:subClassOf";
        Map<String, Set<String>> deletedParents = new HashMap<>();
        for (EdgeDeletion e : edgeDeletions) {
            if (!property.equals(e.propIri())){
                continue;
            }
            deletedParents.computeIfAbsent(e.srcIri(), k -> new HashSet<>()).add(e.tgtIri());
        }

        Map<String, Set<String>> addedParents = new HashMap<>();
        for (EdgeCreation e : edgeCreations) {
            if (!property.equals(e.propIri())){
                continue;
            }
            addedParents.computeIfAbsent(e.srcIri(), k -> new HashSet<>()).add(e.tgtIri());
        }
        Set<NodeMove> result = new HashSet<>();
        for (String child : deletedParents.keySet()) {
            Set<String> olds = deletedParents.get(child);
            Set<String> news = addedParents.getOrDefault(child, Set.of());
            if (news.isEmpty()) continue;
            for (String oldP : olds) {
                for (String newP : news) {
                    if (oldP.equals(newP)){
                        continue;
                    }
                    result.add(new NodeMove(child, oldP, newP));
                }
            }
        }
        return result;
    }

    private Set<PredicateChange> computePredicateChanges(Set<EdgeCreation> edgeCreations,
                                                         Set<EdgeDeletion> edgeDeletions) {
        Map<SrcTgt, Set<String>> deletedProps = new HashMap<>();
        for (EdgeDeletion e : edgeDeletions) {
            SrcTgt key = new SrcTgt(e.srcIri(), e.tgtIri());
            deletedProps.computeIfAbsent(key, k -> new HashSet<>())
                    .add(e.propIri());
        }

        Map<SrcTgt, Set<String>> addedProps = new HashMap<>();
        for (EdgeCreation e : edgeCreations) {
            SrcTgt key = new SrcTgt(e.srcIri(), e.tgtIri());
            addedProps.computeIfAbsent(key, k -> new HashSet<>())
                    .add(e.propIri());
        }

        Set<PredicateChange> result = new HashSet<>();

        for (Map.Entry<SrcTgt, Set<String>> entry : deletedProps.entrySet()) {
            SrcTgt key = entry.getKey();
            Set<String> oldPred = entry.getValue();
            Set<String> newPred = addedProps.getOrDefault(key, Collections.emptySet());
            if (newPred.isEmpty()) continue;

            for (String oldP : oldPred) {
                for (String newP : newPred) {
                    if (oldP.equals(newP)) continue;
                    result.add(new PredicateChange(key.srcIri(), oldP, newP));
                }
            }
        }

        return result;
    }

    private Set<NodeRename> computeNodeRenames(DiffResult diff) {
        OWLOntology original = diff.getOriginal();
        OWLOntology update   = diff.getUpdate();

        Set<OWLClass> oldClasses = original.getClassesInSignature();
        Set<OWLClass> newClasses = update.getClassesInSignature();

        Set<OWLClass> common = new HashSet<>(oldClasses);
        common.retainAll(newClasses);

        Set<NodeRename> result = new HashSet<>();

        for (OWLClass cls : common) {
            IRI iri = cls.getIRI();

            Set<OWLLiteral> oldLabels = getLabels(iri, original);
            Set<OWLLiteral> newLabels = getLabels(iri, update);

            Set<OWLLiteral> removed = new HashSet<>(oldLabels);
            removed.removeAll(newLabels);

            // labels that appeared
            Set<OWLLiteral> added = new HashSet<>(newLabels);
            added.removeAll(oldLabels);

            if (removed.isEmpty() || added.isEmpty()) {
                continue;
            }

            for (OWLLiteral oldLit : removed) {
                for (OWLLiteral newLit : added) {
                    String oldLabel = labelToDisplay(oldLit);
                    String newLabel = labelToDisplay(newLit);
                    if (oldLabel.equals(newLabel)) {
                        continue;
                    }
                    result.add(new NodeRename(
                            iri.toString(),
                            oldLabel,
                            newLabel
                    ));
                }
            }
        }

        return result;
    }

    private Set<OWLLiteral> getLabels(IRI entityIri, OWLOntology ont) {
        Set<OWLLiteral> result = new HashSet<>();

        for (OWLOntology o : ont.getImportsClosure()) {
            for (OWLAnnotationAssertionAxiom ax : o.getAnnotationAssertionAxioms(entityIri)) {
                if (!ax.getProperty().getIRI().equals(OWLRDFVocabulary.RDFS_LABEL.getIRI())) {
                    continue;
                }
                if (ax.getValue() instanceof OWLLiteral lit) {
                    result.add(lit);
                }
            }
        }

        return result;
    }

    private String labelToDisplay(OWLLiteral lit) {
        String s = lit.getLiteral();
        if (lit.hasLang()) {
            s = s + "@" + lit.getLang();
        } else if (!lit.isRDFPlainLiteral() && lit.getDatatype() != null) {
            s = s + "^^" + lit.getDatatype().getIRI();
        }
        return s;
    }

    private Set<OWLLiteral> getDefinitions(IRI entityIri, OWLOntology ont) {
        Set<OWLLiteral> result = new HashSet<>();

        for (OWLOntology o : ont.getImportsClosure()) {
            for (OWLAnnotationAssertionAxiom ax : o.getAnnotationAssertionAxioms(entityIri)) {
                IRI propIri = ax.getProperty().getIRI();
                if (propIri == null) continue;
                if (!TextDefinitionProperties.TEXT_DEFINITION_PROPERTIES.contains(propIri.toString())){
                    continue;
                }
                if (ax.getValue() instanceof OWLLiteral lit) {
                    result.add(lit);
                }
            }
        }
        return result;
    }

    private String definitionToDisplay(OWLLiteral lit) {
        String s = lit.getLiteral();
        if (lit.hasLang()) {
            s = s + "@" + lit.getLang();
        } else if (!lit.isRDFPlainLiteral() && lit.getDatatype() != null) {
            s = s + "^^" + lit.getDatatype().getIRI();
        }
        return s;
    }

    private Set<NewTextDefinition> computeNewTextDefinitions(DiffResult diff) {
        OWLOntology original = diff.getOriginal();
        OWLOntology update   = diff.getUpdate();

        Set<OWLClass> oldClasses = original.getClassesInSignature();
        Set<OWLClass> newClasses = update.getClassesInSignature();

        Set<OWLClass> common = new HashSet<>(oldClasses);
        common.retainAll(newClasses);

        Set<NewTextDefinition> result = new HashSet<>();

        for (OWLClass cls : common) {
            IRI iri = cls.getIRI();

            Set<OWLLiteral> oldDefs = getDefinitions(iri, original);
            Set<OWLLiteral> newDefs = getDefinitions(iri, update);
            Set<OWLLiteral> added = new HashSet<>(newDefs);
            added.removeAll(oldDefs);

            for (OWLLiteral newDef : added) {
                result.add(new NewTextDefinition(
                        iri.toString(),
                        definitionToDisplay(newDef)
                ));
            }
        }

        return result;
    }

    private Set<RemoveTextDefinition> computeRemoveTextDefinitions(DiffResult diff) {
        OWLOntology original = diff.getOriginal();
        OWLOntology update   = diff.getUpdate();

        Set<OWLClass> oldClasses = original.getClassesInSignature();
        Set<OWLClass> newClasses = update.getClassesInSignature();

        Set<OWLClass> common = new HashSet<>(oldClasses);
        common.retainAll(newClasses);

        Set<RemoveTextDefinition> result = new HashSet<>();

        for (OWLClass cls : common) {
            IRI iri = cls.getIRI();

            Set<OWLLiteral> oldDefs = getDefinitions(iri, original);
            Set<OWLLiteral> newDefs = getDefinitions(iri, update);

            Set<OWLLiteral> removed = new HashSet<>(oldDefs);
            removed.removeAll(newDefs);

            for (OWLLiteral oldDef : removed) {
                result.add(new RemoveTextDefinition(
                        iri.toString(),
                        definitionToDisplay(oldDef)
                ));
            }
        }

        return result;
    }

    private Set<NodeTextDefinitionChange> computeNodeTextDefinitionChanges(DiffResult diff) {
        OWLOntology original = diff.getOriginal();
        OWLOntology update   = diff.getUpdate();

        Set<OWLClass> oldClasses = original.getClassesInSignature();
        Set<OWLClass> newClasses = update.getClassesInSignature();

        Set<OWLClass> common = new HashSet<>(oldClasses);
        common.retainAll(newClasses);

        Set<NodeTextDefinitionChange> result = new HashSet<>();

        for (OWLClass cls : common) {
            IRI iri = cls.getIRI();

            Set<OWLLiteral> oldDefs = getDefinitions(iri, original);
            Set<OWLLiteral> newDefs = getDefinitions(iri, update);

            Set<OWLLiteral> removed = new HashSet<>(oldDefs);
            removed.removeAll(newDefs);

            Set<OWLLiteral> added = new HashSet<>(newDefs);
            added.removeAll(oldDefs);

            if (removed.isEmpty() || added.isEmpty()) {
                continue;
            }

            for (OWLLiteral oldLit : removed) {
                for (OWLLiteral newLit : added) {
                    String oldText = definitionToDisplay(oldLit);
                    String newText = definitionToDisplay(newLit);
                    if (oldText.equals(newText)) {
                        continue;
                    }
                    result.add(new NodeTextDefinitionChange(
                            iri.toString(),
                            oldText,
                            newText
                    ));
                }
            }
        }

        return result;
    }

    private Set<NewSynonym> computeNewSynonyms(DiffResult diff) {
        Map<String, Map<String, Set<OWLLiteral>>> addedSyns =
                collectSynonymLiterals(diff.getOnlyInUpdate());

        Set<NewSynonym> result = new HashSet<>();

        for (var entEntry : addedSyns.entrySet()) {
            String entityIri = entEntry.getKey();
            for (var propEntry : entEntry.getValue().entrySet()) {
                String propIri = propEntry.getKey();
                for (OWLLiteral lit : propEntry.getValue()) {
                    result.add(new NewSynonym(
                            entityIri,
                            propIri,
                            literalToDisplay(lit)
                    ));
                }
            }
        }

        return result;
    }

    private Set<RemoveSynonym> computeRemoveSynonyms(DiffResult diff) {
        Map<String, Map<String, Set<OWLLiteral>>> removedSyns =
                collectSynonymLiterals(diff.getOnlyInOriginal());

        Set<RemoveSynonym> result = new HashSet<>();

        for (var entEntry : removedSyns.entrySet()) {
            String entityIri = entEntry.getKey();
            for (var propEntry : entEntry.getValue().entrySet()) {
                String propIri = propEntry.getKey();
                for (OWLLiteral lit : propEntry.getValue()) {
                    result.add(new RemoveSynonym(
                            entityIri,
                            propIri,
                            literalToDisplay(lit)
                    ));
                }
            }
        }

        return result;
    }

    @Override
    public HighLevelDiff from(DiffResult diffResult) {
        Set<NodeCreation> nodeCreations = computeNodeCreations(diffResult);
        Set<NodeDeletion> nodeDeletions = computeNodeDeletions(diffResult);
        Set<ClassCreation> classCreations = computeClassCreations(diffResult);
        Set<NodeObsoletion> nodeObsoletions = computeNodeObsoletions(diffResult);
        Set<SynonymReplacement> synonymReplacements = computeSynonymReplacements(diffResult);
        Set<EdgeCreation> edgeCreations = computeEdgeCreations(diffResult);
        Set<EdgeDeletion> edgeDeletions = computeEdgeDeletions(diffResult);
        Set<NodeMove> nodeMoves = computeNodeMoves(edgeCreations, edgeDeletions);
        Set<PredicateChange> predicateChanges = computePredicateChanges(edgeCreations, edgeDeletions);
        Set<NodeRename> nodeRenames = computeNodeRenames(diffResult);
        Set<NewTextDefinition> newTextDefinitions = computeNewTextDefinitions(diffResult);
        Set<RemoveTextDefinition> removeTextDefinitions = computeRemoveTextDefinitions(diffResult);
        Set<NodeTextDefinitionChange> nodeTextDefinitionChanges = computeNodeTextDefinitionChanges(diffResult);
        Set<NewSynonym> newSynonyms = computeNewSynonyms(diffResult);
        Set<RemoveSynonym> removeSynonyms = computeRemoveSynonyms(diffResult);

        return new HighLevelDiff(
                nodeCreations,
                nodeDeletions,
                nodeObsoletions,
                synonymReplacements,
                edgeCreations,
                edgeDeletions,
                classCreations,
                nodeMoves,
                predicateChanges,
                nodeRenames,
                newTextDefinitions,
                removeTextDefinitions,
                nodeTextDefinitionChanges,
                newSynonyms,
                removeSynonyms
        );
    }
}
