# OWLDiff and OntoDiff

OWLDiff compares two versions of an OWL ontology. This repository also contains **OntoDiff**, an experimental layer that turns axiom-level differences into descriptions of ontology changes.

## How OntoDiff works

OntoDiff uses the syntactic OWLDiff result as its input:

1. Find OWL axioms present only in the original ontology or only in the updated ontology.
2. Generate possible high-level changes from those added and removed axioms.
3. Select a non-overlapping set of candidates using a state search that minimizes cost and uses confidence to break equal-cost ties.
4. Report remaining axioms as annotation or axiom additions/removals.

The current candidate generators cover class creation/deletion, node obsoletion (with or without one direct replacement), subclass placement/removal, node moves and deepenings, IRI refactoring renames, class label renames, `rdfs:comment` changes, OBO synonym additions/removals/replacements, changes to equivalent class definitions, simple relationship creation/deletion, predicate changes, and subset membership changes. [CHANGE_TYPES.md](CHANGE_TYPES.md) defines the removed and added OWL-axiom evidence for each type and identifies changes that currently fall back to generic axiom or annotation additions/removals.

For example, `NodeMove(Flu, Disease, RespiratoryDisease)` is one meaningful change backed by two axioms: remove `SubClassOf(Flu Disease)` and add `SubClassOf(Flu RespiratoryDisease)`. Each side alone could instead be described as `RemoveUnder` or `PlaceUnder`; the search chooses a non-overlapping explanation. We call a change **atomic** if it consumes one axiom, and **compound** if it consumes two or more.

When the new edge object is a proper descendant of the old object in the updated relational graph, OntoDiff also generates the KGCL-shaped `NodeDeepening`: `oldValue`, `newValue`, `aboutEdge`, `oldObjectType`, and `newObjectType`. `aboutEdge` retains the original subject, predicate, and object plus their representations. Direct and transitive paths are supported, and the path may contain edges of any predicate, as required by KGCL. The path axioms are classification context rather than changed evidence, and cycles are excluded because they do not establish proper descendance.

`NodeRename` similarly pairs an old and a new `rdfs:label` assertion for the same existing class when their language and datatype match. Unpaired labels remain annotation additions or removals.

`IriRename(oldIri, newIri)` is a refactoring-level change rather than a label rename. OntoDiff indexes each IRI's changed axioms once, then uses unchanged IRIs and literals in those axioms to find plausible pairs. It substitutes the complete old IRI with the complete new IRI and requires the resulting OWL axiom set to equal the added evidence exactly, then verifies the reverse substitution. A match additionally requires identical OWL entity types, explicit declarations, at least one matching non-declaration axiom, and an unambiguous pairing. Consequently, a pure IRI replacement can consume all related declaration, annotation, incoming-edge, outgoing-edge, and logical-axiom differences as one change; an IRI replacement combined with another edit remains separate evidence.

`NodeObsoletion(N)` recognizes a newly added `AnnotationAssertion(owl:deprecated N true)` for a node that exists in both ontology versions. If the update also adds exactly one `AnnotationAssertion(IAO:0100001 N R)`, the stronger `NodeObsoletionWithDirectReplacement(N, R)` candidate consumes both assertions. The replacement may be encoded as an IRI or, as found in some OBO ontologies, an IRI/CURIE-like literal. Several direct-replacement assertions are ambiguous, so they are not paired automatically.

`NodeRename` and `CommentChange` keep each affected literal as a `LiteralValue` containing its lexical form, language, and datatype IRI. Their `ChangeCandidate` also retains the complete removed and added annotation axioms as evidence. When a node rename is exported to RDF, its `kgcl:old_value` and `kgcl:new_value` remain language-tagged or typed literals rather than plain strings.

Candidate confidence must be in `(0, 1]`. Directly observed changes use `1.0`; unambiguously paired compound changes—including `IriRename` and relational-graph-supported `NodeDeepening`—use `0.95`; structural inferences such as generic `NodeMove` use `0.9`; and weaker interpretations such as `PredicateChange` and `SynonymReplacement` use `0.8`. For `NodeMove`, `NodeDeepening`, and `PredicateChange`, the base value is divided by the number of compatible pairings when pairing is ambiguous. The search accumulates `-log(confidence)` and uses the total only to break ties between explanations with the same cost. These values are heuristic scores rather than calibrated probabilities.

OntoDiff currently works from OWL axioms, not an RDF triple diff or semantic entailments. The `ontodiff` module requires Java 17.
