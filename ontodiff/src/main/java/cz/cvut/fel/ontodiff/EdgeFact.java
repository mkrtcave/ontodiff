package cz.cvut.fel.ontodiff;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import java.util.Optional;

public record EdgeFact (
        String subject,
        String predicate,
        String object,
        OWLAxiom sourceAxiom
) {
    private static final String SUBCLASS_OF =
            "http://www.w3.org/2000/01/rdf-schema#subClassOf";

    private Optional<EdgeFact> extractSimpleEdge(OWLAxiom axiom) {
        if (axiom instanceof OWLSubClassOfAxiom subClassAxiom
                && !subClassAxiom.getSubClass().isAnonymous()
                && !subClassAxiom.getSuperClass().isAnonymous()) {

            return Optional.of(new EdgeFact(
                    subClassAxiom.getSubClass()
                            .asOWLClass()
                            .getIRI()
                            .toString(),
                    SUBCLASS_OF,
                    subClassAxiom.getSuperClass()
                            .asOWLClass()
                            .getIRI()
                            .toString(),
                    axiom
            ));
        }

        return Optional.empty();
    }
}
