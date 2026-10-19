package cz.cvut.fel.ontodiff.export;

import cz.cvut.fel.ontodiff.changes.LiteralValue;
import cz.cvut.fel.ontodiff.service.HighLevelDiffService;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.net.URI;
import java.net.URISyntaxException;

public class RdfDiffModelBuilderImpl implements RdfDiffModelBuilder {

    private static final String KGCL_NS = "https://w3id.org/kgcl/";
    private static final String OND_NS  = "https://w3id.org/ontodiff/schema#";
    private static final String PROV_NS = "http://www.w3.org/ns/prov#";
    private static final String XSD_NS  = "http://www.w3.org/2001/XMLSchema#";

    @Override
    public Dataset build(HighLevelDiffService.HighLevelDiff highLevelDiff,
                         DiffRunMetadata metadata,
                         String diffGraphIri) {

        Dataset dataset = DatasetFactory.create();

        Model defaultModel = dataset.getDefaultModel();
        setPrefixes(defaultModel);

        Model diffGraphModel = dataset.getNamedModel(diffGraphIri);
        setPrefixes(diffGraphModel);

        Resource diffRun = resourceOrBnode(defaultModel, metadata.diffRunIri());
        Resource diffRunClass = defaultModel.createResource(OND_NS + "DiffRun");
        diffRun.addProperty(RDF.type, diffRunClass);

        Resource fromOnt = resourceOrBnode(defaultModel, metadata.fromOntologyVersionIri());
        Resource toOnt = resourceOrBnode(defaultModel, metadata.toOntologyVersionIri());

        Property fromOntProp = defaultModel.createProperty(OND_NS, "fromOntology");
        Property toOntProp = defaultModel.createProperty(OND_NS, "toOntology");

        diffRun.addProperty(fromOntProp, fromOnt);
        diffRun.addProperty(toOntProp,   toOnt);

        Property provUsed = defaultModel.createProperty(PROV_NS, "used");
        Property provGenAt = defaultModel.createProperty(PROV_NS, "generatedAtTime");
        Property provAssocW = defaultModel.createProperty(PROV_NS, "wasAssociatedWith");
        Property provGenerated = defaultModel.createProperty(PROV_NS, "generated");

        diffRun.addProperty(provUsed, fromOnt);
        diffRun.addProperty(provUsed, toOnt);

        if (metadata.generatedAt() != null) {
            diffRun.addLiteral(
                    provGenAt,
                    defaultModel.createTypedLiteral(metadata.generatedAt().toString(), XSDDatatype.XSDdateTime)
            );
        }

        String toolLocal = sanitizeLocalName((metadata.toolName() == null ? "tool" : metadata.toolName()) + "-" +
                                                (metadata.toolVersion() == null ? "0"    : metadata.toolVersion())
        );
        Resource toolAgent = defaultModel.createResource(OND_NS + "tool/" + toolLocal);
        Resource softwareAgentClass = defaultModel.createResource(PROV_NS + "SoftwareAgent");
        toolAgent.addProperty(RDF.type, softwareAgentClass);
        toolAgent.addProperty(RDFS.label, (metadata.toolName() == null ? "tool" : metadata.toolName()) + " " +
                                                (metadata.toolVersion() == null ? "" : metadata.toolVersion()));
        diffRun.addProperty(provAssocW, toolAgent);

        if (metadata.projectIri() != null && !metadata.projectIri().isBlank()) {
            Resource project = resourceOrBnode(defaultModel, metadata.projectIri());
            Resource projectClass = defaultModel.createResource(OND_NS + "DiffProject");
            project.addProperty(RDF.type, projectClass);
            Property inProject = defaultModel.createProperty(OND_NS, "inProject");
            diffRun.addProperty(inProject, project);
        }

        Property usesAlgorithm = defaultModel.createProperty(OND_NS, "usesAlgorithm");
        diffRun.addLiteral(usesAlgorithm, (metadata.toolName() == null ? "tool" : metadata.toolName()) + " " +
                                            (metadata.toolVersion() == null ? "" : metadata.toolVersion()));

        Property kgclId = diffGraphModel.createProperty(KGCL_NS, "id");
        Property changeSetProp = diffGraphModel.createProperty(KGCL_NS, "change_set");
        Property aboutNodeProp = diffGraphModel.createProperty(KGCL_NS, "about_node");
        Property provGenerated2 = diffGraphModel.createProperty(PROV_NS, "generated");

        String txBase = isLikelyValidIri(metadata.diffRunIri()) ? metadata.diffRunIri() : OND_NS + "tx";
        String txIri = txBase + "#tx";
        Resource tx = resourceOrBnode(diffGraphModel, txIri);
        Resource txClass = diffGraphModel.createResource(KGCL_NS + "Transaction");
        tx.addProperty(RDF.type, txClass);

        String txIdValue = metadata.generatedAt() != null ? metadata.generatedAt().toString() : "unknown";
        tx.addLiteral(kgclId, "TX-" + txIdValue);

        String resultIri = isLikelyValidIri(metadata.diffRunIri())
                ? metadata.diffRunIri() + "#result"
                : null;
        Resource diffResult = resourceOrBnode(defaultModel, resultIri);
        Resource diffResultClass = defaultModel.createResource(OND_NS + "DiffResult");
        diffResult.addProperty(RDF.type, diffResultClass);

        Property result = defaultModel.createProperty(OND_NS, "result");
        Property hasKgclTransaction = defaultModel.createProperty(OND_NS, "hasKgclTransaction");
        diffRun.addProperty(result, diffResult);
        diffResult.addProperty(hasKgclTransaction, tx);
        diffRun.addProperty(provGenerated, diffResult);

        int changeCounter = 1;

        for (HighLevelDiffService.NodeCreation c : highLevelDiff.nodeCreations()) {
            Resource ch = createChangeResource(diffGraphModel, "NodeCreation", metadata, changeCounter++);
            Resource node = resourceOrBnode(diffGraphModel, c.iri());
            ch.addProperty(aboutNodeProp, node);
            tx.addProperty(changeSetProp, ch);
        }

        for (HighLevelDiffService.NodeDeletion c : highLevelDiff.nodeDeletions()) {
            Resource ch = createChangeResource(diffGraphModel, "NodeDeletion", metadata, changeCounter++);
            Resource node = resourceOrBnode(diffGraphModel, c.iri());
            ch.addProperty(aboutNodeProp, node);
            tx.addProperty(changeSetProp, ch);
        }

        // NodeObsoletion
        for (HighLevelDiffService.NodeObsoletion c : highLevelDiff.nodeObsoletions()) {
            Resource ch = createChangeResource(diffGraphModel, "NodeObsoletion", metadata, changeCounter++);
            Resource node = resourceOrBnode(diffGraphModel, c.iri());
            ch.addProperty(aboutNodeProp, node);
            if (c.reason() != null) {
                Property reasonProp = diffGraphModel.createProperty(OND_NS, "obsoletionReason");
                ch.addLiteral(reasonProp, c.reason());
            }
            tx.addProperty(changeSetProp, ch);
        }

        // NodeRename
        for (HighLevelDiffService.NodeRename c : highLevelDiff.nodeRenames()) {
            Resource ch = createChangeResource(diffGraphModel, "NodeRename", metadata, changeCounter++);
            Resource node = resourceOrBnode(diffGraphModel, c.classIri());
            ch.addProperty(aboutNodeProp, node);
            Property oldVal = diffGraphModel.createProperty(KGCL_NS, "old_value");
            Property newVal = diffGraphModel.createProperty(KGCL_NS, "new_value");
            ch.addProperty(oldVal, toRdfLiteral(diffGraphModel, c.oldLabel()));
            ch.addProperty(newVal, toRdfLiteral(diffGraphModel, c.newLabel()));
            tx.addProperty(changeSetProp, ch);
        }

        // NewTextDefinition
        for (HighLevelDiffService.NewTextDefinition c : highLevelDiff.newTextDefinitions()) {
            Resource ch = createChangeResource(diffGraphModel, "NewTextDefinition", metadata, changeCounter++);
            Resource node = resourceOrBnode(diffGraphModel, c.classIri());
            ch.addProperty(aboutNodeProp, node);
            Property newVal = diffGraphModel.createProperty(KGCL_NS, "new_value");
            ch.addLiteral(newVal, c.definition());
            tx.addProperty(changeSetProp, ch);
        }

        // RemoveTextDefinition
        for (HighLevelDiffService.RemoveTextDefinition c : highLevelDiff.removeTextDefinitions()) {
            Resource ch = createChangeResource(diffGraphModel, "RemoveTextDefinition", metadata, changeCounter++);
            Resource node = resourceOrBnode(diffGraphModel, c.classIri());
            ch.addProperty(aboutNodeProp, node);
            Property oldVal = diffGraphModel.createProperty(KGCL_NS, "old_value");
            ch.addLiteral(oldVal, c.definition());
            tx.addProperty(changeSetProp, ch);
        }

        // NodeTextDefinitionChange
        for (HighLevelDiffService.NodeTextDefinitionChange c : highLevelDiff.nodeTextDefinitionChanges()) {
            Resource ch = createChangeResource(diffGraphModel, "NodeTextDefinitionChange", metadata, changeCounter++);
            Resource node = resourceOrBnode(diffGraphModel, c.classIri());
            ch.addProperty(aboutNodeProp, node);
            Property oldVal = diffGraphModel.createProperty(KGCL_NS, "old_value");
            Property newVal = diffGraphModel.createProperty(KGCL_NS, "new_value");
            ch.addLiteral(oldVal, c.oldDefinition());
            ch.addLiteral(newVal, c.newDefinition());
            tx.addProperty(changeSetProp, ch);
        }

        // NewSynonym
        for (HighLevelDiffService.NewSynonym c : highLevelDiff.newSynonyms()) {
            Resource ch = createChangeResource(diffGraphModel, "NewSynonym", metadata, changeCounter++);
            Resource node = resourceOrBnode(diffGraphModel, c.entityIri());
            ch.addProperty(aboutNodeProp, node);
            Property newVal = diffGraphModel.createProperty(KGCL_NS, "new_value");
            ch.addLiteral(newVal, c.synonym());
            Property synProp = diffGraphModel.createProperty(OND_NS, "synonymProperty");
            ch.addProperty(synProp, resourceOrBnode(diffGraphModel, c.propertyIri()));
            tx.addProperty(changeSetProp, ch);
        }

        // RemoveSynonym
        for (HighLevelDiffService.RemoveSynonym c : highLevelDiff.removeSynonyms()) {
            Resource ch = createChangeResource(diffGraphModel, "RemoveSynonym", metadata, changeCounter++);
            Resource node = resourceOrBnode(diffGraphModel, c.entityIri());
            ch.addProperty(aboutNodeProp, node);
            Property oldVal = diffGraphModel.createProperty(KGCL_NS, "old_value");
            ch.addLiteral(oldVal, c.synonym());
            Property synProp = diffGraphModel.createProperty(OND_NS, "synonymProperty");
            ch.addProperty(synProp, resourceOrBnode(diffGraphModel, c.propertyIri()));
            tx.addProperty(changeSetProp, ch);
        }

        // SynonymReplacement
        for (HighLevelDiffService.SynonymReplacement c : highLevelDiff.synonymReplacement()) {
            Resource ch = createChangeResource(diffGraphModel, "NodeSynonymChange", metadata, changeCounter++);
            Resource node = resourceOrBnode(diffGraphModel, c.entityIri());
            ch.addProperty(aboutNodeProp, node);
            Property oldVal = diffGraphModel.createProperty(KGCL_NS, "old_value");
            Property newVal = diffGraphModel.createProperty(KGCL_NS, "new_value");
            ch.addLiteral(oldVal, c.oldSynonym());
            ch.addLiteral(newVal, c.newSynonym());
            Property synProp = diffGraphModel.createProperty(OND_NS, "synonymProperty");
            ch.addProperty(synProp, resourceOrBnode(diffGraphModel, c.propertyIri()));
            tx.addProperty(changeSetProp, ch);
        }

        // NodeMove
        for (HighLevelDiffService.NodeMove c : highLevelDiff.nodeMoves()) {
            Resource ch = createChangeResource(diffGraphModel, "NodeMove", metadata, changeCounter++);
            Resource node = resourceOrBnode(diffGraphModel, c.chldIri());
            ch.addProperty(aboutNodeProp, node);
            Property oldParent = diffGraphModel.createProperty(OND_NS, "oldParent");
            Property newParent = diffGraphModel.createProperty(OND_NS, "newParent");
            ch.addProperty(oldParent, resourceOrBnode(diffGraphModel, c.oldPrtIri()));
            ch.addProperty(newParent, resourceOrBnode(diffGraphModel, c.newPrtIri()));
            tx.addProperty(changeSetProp, ch);
        }

        // PredicateChange
        for (HighLevelDiffService.PredicateChange c : highLevelDiff.predicateChanges()) {
            Resource ch = createChangeResource(diffGraphModel, "PredicateChange", metadata, changeCounter++);

            Property oldVal = diffGraphModel.createProperty(KGCL_NS, "old_value");
            Property newVal = diffGraphModel.createProperty(KGCL_NS, "new_value");
            ch.addLiteral(oldVal, c.oldPrpIri());
            ch.addLiteral(newVal, c.newPrpIri());

            // about_edge (subject only, unless you extend your record)
            Resource edge = diffGraphModel.createResource();
            Property subjectProp = diffGraphModel.createProperty(KGCL_NS, "subject");
            edge.addProperty(subjectProp, resourceOrBnode(diffGraphModel, c.srcIri()));
            Property aboutEdge = diffGraphModel.createProperty(KGCL_NS, "about_edge");
            ch.addProperty(aboutEdge, edge);

            tx.addProperty(changeSetProp, ch);
        }

        // EdgeCreation
        for (HighLevelDiffService.EdgeCreation c : highLevelDiff.edgeCreations()) {
            Resource ch = createChangeResource(diffGraphModel, "EdgeCreation", metadata, changeCounter++);
            Resource edge = diffGraphModel.createResource();
            Property subjectProp   = diffGraphModel.createProperty(KGCL_NS, "subject");
            Property predicateProp = diffGraphModel.createProperty(KGCL_NS, "predicate");
            Property objectProp    = diffGraphModel.createProperty(KGCL_NS, "object");
            edge.addProperty(subjectProp,   resourceOrBnode(diffGraphModel, c.srcIri()));
            edge.addProperty(predicateProp, resourceOrBnode(diffGraphModel, c.propIri()));
            edge.addProperty(objectProp,    resourceOrBnode(diffGraphModel, c.tgtIri()));
            Property aboutEdge = diffGraphModel.createProperty(KGCL_NS, "about_edge");
            ch.addProperty(aboutEdge, edge);
            tx.addProperty(changeSetProp, ch);
        }

        // EdgeDeletion
        for (HighLevelDiffService.EdgeDeletion c : highLevelDiff.edgeDeletions()) {
            Resource ch = createChangeResource(diffGraphModel, "EdgeDeletion", metadata, changeCounter++);
            Resource edge = diffGraphModel.createResource();
            Property subjectProp   = diffGraphModel.createProperty(KGCL_NS, "subject");
            Property predicateProp = diffGraphModel.createProperty(KGCL_NS, "predicate");
            Property objectProp    = diffGraphModel.createProperty(KGCL_NS, "object");
            edge.addProperty(subjectProp,   resourceOrBnode(diffGraphModel, c.srcIri()));
            edge.addProperty(predicateProp, resourceOrBnode(diffGraphModel, c.propIri()));
            edge.addProperty(objectProp,    resourceOrBnode(diffGraphModel, c.tgtIri()));
            Property aboutEdge = diffGraphModel.createProperty(KGCL_NS, "about_edge");
            ch.addProperty(aboutEdge, edge);
            tx.addProperty(changeSetProp, ch);
        }

        return dataset;
    }

    private static void setPrefixes(Model model) {
        model.setNsPrefix("kgcl", KGCL_NS);
        model.setNsPrefix("ond",  OND_NS);
        model.setNsPrefix("prov", PROV_NS);
        model.setNsPrefix("rdf",  RDF.getURI());
        model.setNsPrefix("rdfs", RDFS.getURI());
        model.setNsPrefix("xsd",  XSD_NS);
    }

    private static Resource createChangeResource(Model model,
                                                 String kgclTypeLocalName,
                                                 DiffRunMetadata meta,
                                                 int index) {
        String base = isLikelyValidIri(meta.diffRunIri())
                ? meta.diffRunIri()
                : OND_NS + "change";
        String changeIri = base + "#change-" + kgclTypeLocalName + "-" + index;
        Resource change = resourceOrBnode(model, changeIri);
        Resource type   = model.createResource(KGCL_NS + kgclTypeLocalName);
        change.addProperty(RDF.type, type);

        Property idProp = model.createProperty(KGCL_NS, "id");
        change.addLiteral(idProp, "CHANGE:" + kgclTypeLocalName + ":" + index);

        return change;
    }

    private static boolean isLikelyValidIri(String iri) {
        if (iri == null || iri.isBlank()) {
            return false;
        }
        try {
            URI u = new URI(iri);
            return u.isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static Resource resourceOrBnode(Model model, String iri) {
        if (isLikelyValidIri(iri)) {
            return model.createResource(iri);
        } else {
            return model.createResource();
        }
    }

    private static Literal toRdfLiteral(Model model, LiteralValue value) {
        if (value.hasLanguage()) {
            return model.createLiteral(value.lexicalForm(), value.language());
        }

        return model.createTypedLiteral(
                value.lexicalForm(),
                TypeMapper.getInstance().getSafeTypeByName(value.datatypeIri())
        );
    }

    private static String sanitizeLocalName(String s) {
        if (s == null) return "unnamed";
        return s.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
