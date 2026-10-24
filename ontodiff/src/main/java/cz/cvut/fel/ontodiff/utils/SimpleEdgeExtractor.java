package cz.cvut.fel.ontodiff.utils;

import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.util.Optional;

public class SimpleEdgeExtractor {

    public static final String SUBCLASS_OF =
            OWLRDFVocabulary.RDFS_SUBCLASS_OF.getIRI().toString();

    public Optional<EdgeFact> extract(OWLAxiom axiom) {
        if (!(axiom instanceof OWLSubClassOfAxiom subclass) || subclass.getSubClass().isAnonymous()) {
            return Optional.empty();
        }

        String child = subclass.getSubClass().asOWLClass().getIRI().toString();

        if (!subclass.getSuperClass().isAnonymous()) {
            return Optional.of(new EdgeFact(child, SUBCLASS_OF, subclass.
                    getSuperClass().asOWLClass().getIRI().toString(),
                    axiom
            ));
        }

        if (subclass.getSuperClass() instanceof OWLObjectSomeValuesFrom some
                && !some.getProperty().isAnonymous()
                && !some.getFiller().isAnonymous()) {
            return Optional.of(new EdgeFact(child, some.getProperty().asOWLObjectProperty().getIRI().toString(),
                    some.getFiller().asOWLClass().getIRI().toString(), axiom));
        }

        return Optional.empty();
    }
}
