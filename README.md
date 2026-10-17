# OWLDiff and OntoDiff

OWLDiff compares two versions of an OWL ontology. This repository also contains **OntoDiff**, an experimental layer that turns axiom-level differences into descriptions of ontology changes.

## How OntoDiff works

OntoDiff uses the syntactic OWLDiff result as its input:

1. Find OWL axioms present only in the original ontology or only in the updated ontology.
2. Generate possible high-level changes from those added and removed axioms.
3. Select a non-overlapping set of candidates using a cost-based state search.
4. Report remaining axioms as annotation or axiom additions/removals.

The current candidate generators cover class creation/deletion, subclass placement/removal, node moves, class label renames, `rdfs:comment` changes, changes to equivalent class definitions, simple relationship creation/deletion, predicate changes, and subset membership changes. [CHANGE_TYPES.md](CHANGE_TYPES.md) defines the removed and added OWL-axiom evidence for each type and identifies changes that currently fall back to generic axiom or annotation additions/removals.

For example, `NodeMove(Flu, Disease, RespiratoryDisease)` is one meaningful change backed by two axioms: remove `SubClassOf(Flu Disease)` and add `SubClassOf(Flu RespiratoryDisease)`. Each side alone could instead be described as `RemoveUnder` or `PlaceUnder`; the search chooses a non-overlapping explanation. We call a change **atomic** if it consumes one axiom, and **compound** if it consumes two or more.

`NodeRename` similarly pairs an old and a new `rdfs:label` assertion for the same existing class when their language and datatype match. Unpaired labels remain annotation additions or removals.

OntoDiff currently works from OWL axioms, not an RDF triple diff or semantic entailments. The `ontodiff` module requires Java 17.
