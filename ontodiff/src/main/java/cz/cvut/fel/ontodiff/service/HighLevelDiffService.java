package cz.cvut.fel.ontodiff.service;

import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.changes.LiteralValue;
import cz.cvut.fel.ontodiff.changes.ProposedChange;
import org.semanticweb.owlapi.model.OWLClassExpression;

import java.util.Set;

public interface HighLevelDiffService {

    // implemented
    record NodeCreation(String iri) implements ProposedChange {}
    record ClassCreation(String iri) implements ProposedChange {}
    record NodeDeletion(String iri) implements ProposedChange {}
    record EdgeCreation(String srcIri, String propIri, String tgtIri) implements ProposedChange {}
    record EdgeDeletion(String srcIri, String propIri, String tgtIri) implements ProposedChange {}
    record NodeMove(String chldIri, String oldPrtIri, String newPrtIri) implements ProposedChange {}
    record PlaceUnder(String childIri, String parentIri) implements ProposedChange {}
    record RemoveUnder(String childIri, String parentIri) implements ProposedChange {}
    record AddNodeToSubset(String nodeIri, String subsetIri) implements ProposedChange {}
    record RemoveNodeFromSubset(String nodeIri, String subsetIri) implements ProposedChange {}
    record PredicateChange(String srcIri,String tgtIri, String oldPrpIri, String newPrpIri) implements ProposedChange {}
    record EquivalentClassesChange(
            String classIri,
            OWLClassExpression oldExpression,
            OWLClassExpression newExpression
    ) implements ProposedChange {}
    record CommentChange(
            String subjectIri,
            LiteralValue oldComment,
            LiteralValue newComment
    ) implements ProposedChange {}
    record CommentAddition(String subjectIri, String comment) implements ProposedChange {}
    record CommentRemoval(String subjectIri, String comment) implements ProposedChange {}
    record NewSynonym(String entityIri, String propertyIri, String synonym) implements ProposedChange {}
    record RemoveSynonym(String entityIri, String propertyIri, String synonym) implements ProposedChange {}
    record SynonymReplacement(String entityIri, String propertyIri, String oldSynonym, String newSynonym) implements ProposedChange {}


    record NodeObsoletion(String iri, String reason) implements ProposedChange {}
    record NodeObsoletionWithDirectReplacement(
            String iri,
            String replacement
    ) implements ProposedChange {}
    record SrcTgt(String srcIri, String tgtIri) implements ProposedChange {}
    record NodeRename(
            String classIri,
            LiteralValue oldLabel,
            LiteralValue newLabel
    ) implements ProposedChange {}
    record NewTextDefinition(String classIri, String definition) implements ProposedChange {}
    record RemoveTextDefinition(String classIri, String definition) implements ProposedChange {}
    record NodeTextDefinitionChange(String classIri, String oldDefinition, String newDefinition) implements ProposedChange {}

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
            Set<RemoveSynonym> removeSynonyms,
            Set<PlaceUnder> placeUnders,
            Set<RemoveUnder> removeUnders
    ) {}

    HighLevelDiff from(DiffResult diffResult);
}
