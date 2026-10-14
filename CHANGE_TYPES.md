# Ontology change types

| Change type | Meaning | Evidence recognized by the current generator |
| --- | --- | --- |
| `NodeCreation` | A new class appears. | An added class declaration whose class IRI is absent from the original ontology's signature. |
| `NodeDeletion` | A class disappears. | A removed class declaration whose class IRI is absent from the updated ontology's signature. Its candidate also covers removed annotations on that class and removed `SubClassOf` axioms with that class as the subclass. |
| `PlaceUnder` | Give a class a parent. | Added `SubClassOf(A B)` with named classes `A` and `B`. |
| `RemoveUnder` | Remove a class's parent. | Removed `SubClassOf(A B)` with named classes `A` and `B`. |
| `NodeMove` | Change a class's parent. | Removed `SubClassOf(A B)` and added `SubClassOf(A C)` for the same named child `A` and different named parents. |
| `EdgeCreation` | Add a non-subclass relationship between classes. | Added `SubClassOf(A ObjectSomeValuesFrom(p B))` with named class `A`, named property `p`, and named class `B`; reported as `(A, p, B)`. |
| `EdgeDeletion` | Remove a non-subclass relationship between classes. | The removed version of the same `ObjectSomeValuesFrom` pattern. |
| `PredicateChange` | Change the relationship type while keeping its endpoints. | A removed edge `(A, p, B)` and an added edge `(A, q, B)` where `p != q`. Either predicate may be `rdfs:subClassOf`. |
| `AddNodeToSubset` | Add a node to a subset. | Added `oboInOwl:inSubset` annotation assertion with an IRI subject and IRI subset value. |
| `RemoveNodeFromSubset` | Remove a node from a subset. | Removed version of that `oboInOwl:inSubset` annotation assertion. |