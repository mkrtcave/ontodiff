# Ontology change types

OntoDiff currently compares only **OWL axioms**, not RDF triples yet. Let `R` be the axioms found only in the original ontology and `A` the axioms found only in the updated ontology. A candidate change has **removed evidence** from `R` and **added evidence** from `A`. Applying a candidate consumes that evidence, so another selected candidate cannot explain the same axiom.

An **atomic** change consumes one OWL axiom ( e.g. addition or removal). A **compound** change consumes two or more axioms. This counts evidence, not the number of meaningful operations: `NodeMove` is one operation explained by two axiom changes. A single OWL axiom, especially one containing a restriction, may be represented by several RDF triples and blank nodes.

In the table, `C`, `P`, and `Q` are named classes. For `EdgeCreation` and `EdgeDeletion`, `p` is a named object property; in `PredicateChange`, `p` and `q` can also be `rdfs:subClassOf`. `AnnotationAssertion(prop C value)` means an annotation whose subject is the IRI of `C`. `edge(C, p, P)` is shorthand for `SubClassOf(C ObjectSomeValuesFrom(p P))`; `edge(C, rdfs:subClassOf, P)` means `SubClassOf(C P)`.

| Change type | Removed evidence | Added evidence | Condition                                                                                                                      |
| --- | --- | --- |--------------------------------------------------------------------------------------------------------------------------------|
| `NodeCreation(C)` | — | `Declaration(Class(C))`, plus any added annotations on `C` and outgoing `SubClassOf(C X)` axioms | Compound. Requires `C` to be absent from the original class signature.                                                         |
| `NodeDeletion(C)` | `Declaration(Class(C))`, plus any removed annotations on `C` and outgoing `SubClassOf(C X)` axioms | — | Compound. Requires `C` to be absent from the updated class signature.                                                          |
| `PlaceUnder(C, P)` | — | `SubClassOf(C P)` | Atomic.                                                                                                                        |
| `RemoveUnder(C, P)` | `SubClassOf(C P)` | — | Atomic.                                                                                                                        |
| `NodeMove(C, P, Q)` | `SubClassOf(C P)` | `SubClassOf(C Q)` | Compound; `P` and `Q` must differ.                                                                                             |
| `NodeRename(C, old, new)` | `AnnotationAssertion(rdfs:label C old)` | `AnnotationAssertion(rdfs:label C new)` | Compound. `C` must remain a class in both versions; labels must have the same language and datatype, with one unambiguous pairing. |
| `EdgeCreation(C, p, P)` | — | `edge(C, p, P)` | Atomic. This is a class restriction, not an arbitrary RDF triple or creation of the property entity `p`.                       |
| `EdgeDeletion(C, p, P)` | `edge(C, p, P)` | — | Atomic; the reverse of `EdgeCreation`.                                                                                         |
| `PredicateChange(C, P, p, q)` | `edge(C, p, P)` | `edge(C, q, P)` | Compound; `p` and `q` must differ. Either side can instead use `rdfs:subClassOf` and its corresponding `SubClassOf(C P)` axiom. |
| `AddNodeToSubset(C, S)` | — | `AnnotationAssertion(oboInOwl:inSubset C S)` | Atomic; `C` and `S` are IRIs.                                                                                                  |
| `RemoveNodeFromSubset(C, S)` | `AnnotationAssertion(oboInOwl:inSubset C S)` | — | Atomic; the reverse of `AddNodeToSubset`.                                                                                      |

These are **candidate patterns**, not guaranteed final classifications. For example, removing `SubClassOf(C P)` and adding `SubClassOf(C Q)` produces a `NodeMove` candidate as well as separate `RemoveUnder` and `PlaceUnder` candidates. The search chooses a non-overlapping explanation according to candidate cost. A class creation/deletion candidate can also consume annotations and outgoing subclass axioms that otherwise would be separate changes. 

For labels, only an unambiguous old/new pair on the same existing class becomes `NodeRename`. A label added or removed on its own, a language/datatype change, or multiple changed labels with no unique pairing remains `AnnotationAddition` or `AnnotationRemoval` after the search. The candidate records the old and new text; its evidence axioms retain the full literals, including language and datatype.

### `NodeRename` example

Suppose the class IRI `http://example.org/Flu` exists in both ontology versions, but its English label changes:

```text
Removed: AnnotationAssertion(rdfs:label <http://example.org/Flu> "Influenza"@en)
Added:   AnnotationAssertion(rdfs:label <http://example.org/Flu> "Flu"@en)

Result:  NodeRename(http://example.org/Flu, "Influenza", "Flu")
```

The generator proposes one `NodeRename` with the removed and added assertions as evidence. The state search consumes both if it selects that candidate, rather than reporting two annotation fallbacks. This is one meaningful operation with **compound evidence** (two OWL axioms). Only the display label changes: `http://example.org/Flu` remains the same IRI, so this is **not an IRI rename**.
