package cz.cvut.fel.ontodiff;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.util.Optional;

public class SimpleEdgeExtractor {

    private static final String SUBCLASS_OF = OWLRDFVocabulary.RDFS_SUBCLASS_OF
            .getIRI()
            .toString();

    public Optional<EdgeFact> extract(OWLAxiom axiom) {
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
