package cz.cvut.fel.ontodiff.service;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.ProposedChange;

import java.util.Set;

public interface HighLevelDiffService {

    record NodeCreation(String iri) implements ProposedChange {}
    record NodeDeletion(String iri) {}
    record NodeObsoletion(String iri, String reason) {}
    record SynonymReplacement( String entityIri, String propertyIri, String oldSynonym, String newSynonym) {}
    record EdgeCreation(String srcIri, String propIri, String tgtIri) {}
    record EdgeDeletion(String srcIri, String propIri, String tgtIri) {}
    record ClassCreation(String iri) {}
    record NodeMove(String chldIri, String oldPrtIri, String newPrtIri) implements ProposedChange {}
    record SrcTgt(String srcIri, String tgtIri) {}
    record PredicateChange(String srcIri, String oldPrpIri, String newPrpIri) {}
    record NodeRename(String classIri, String oldLabel, String newLabel) {}
    record NewTextDefinition(String classIri, String definition) {}
    record RemoveTextDefinition(String classIri, String definition) {}
    record NodeTextDefinitionChange(String classIri, String oldDefinition, String newDefinition) {}
    record NewSynonym(String entityIri, String propertyIri, String synonym) {}
    record RemoveSynonym(String entityIri, String propertyIri, String synonym) {}

    record HighLevelDiff(
            Set<NodeCreation> nodeCreations,
            Set<NodeDeletion> nodeDeletions,
            Set<NodeObsoletion> nodeObsoletions,
            Set<SynonymReplacement> synonymReplacement,
            Set<EdgeCreation> edgeCreations,
            Set<EdgeDeletion> edgeDeletions,
            Set<ClassCreation> classCreations,
            Set<NodeMove> nodeMoves,
            Set<PredicateChange> predicateChanges,
            Set<NodeRename> nodeRenames,
            Set<NewTextDefinition> newTextDefinitions,
            Set<RemoveTextDefinition> removeTextDefinitions,
            Set<NodeTextDefinitionChange> nodeTextDefinitionChanges,
            Set<NewSynonym> newSynonyms,
            Set<RemoveSynonym> removeSynonyms
    ) {}

    HighLevelDiff from(DiffResult diffResult);
}
