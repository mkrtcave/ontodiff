package cz.cvut.fel.ontodiff.changes;

import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;

public interface FallbackChange extends ProposedChange {

    record AnnotationAddition(OWLAnnotationAssertionAxiom axiom) implements FallbackChange {}
    record AnnotationRemoval(OWLAnnotationAssertionAxiom axiom) implements FallbackChange {}
    record AxiomAddition(OWLAxiom axiom) implements FallbackChange {}
    record AxiomRemoval(OWLAxiom axiom) implements FallbackChange {}
}
