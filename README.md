# OWLDiff and OntoDiff

OWLDiff compares two versions of an OWL ontology. This repository also contains **OntoDiff**, an experimental layer that turns axiom-level differences into descriptions of ontology changes.

## How OntoDiff works

OntoDiff uses the syntactic OWLDiff result as its input:

1. Find OWL axioms present only in the original ontology or only in the updated ontology.
2. Generate possible high-level changes from those added and removed axioms.
3. Select a non-overlapping set of candidates using a cost-based state search.
4. Report remaining axioms as annotation or axiom additions/removals.

The current candidate generators cover class creation/deletion, subclass placement/removal, node moves, simple relationship creation/deletion, predicate changes, and subset membership changes. See [CHANGE_TYPES.md](CHANGE_TYPES.md) for the exact change types.

OntoDiff currently works from OWL axioms, not an RDF triple diff or semantic entailments. The `ontodiff` module requires Java 17.
