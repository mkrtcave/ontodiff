package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;
import cz.cvut.fel.ontodiff.EdgeFact;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import java.util.Optional;
import java.util.stream.Stream;

import static org.semanticweb.owlapi.model.AxiomType.SUBCLASS_OF;

public interface CandidateGenerator {

    Stream<ChangeCandidate> generate(DiffResult diff);

//    default Optional<EdgeFact> extractSimpleEdge(OWLAxiom axiom) {
//        if (axiom instanceof OWLSubClassOfAxiom subClassAxiom
//                && !subClassAxiom.getSubClass().isAnonymous()
//                && !subClassAxiom.getSuperClass().isAnonymous()) {
//
//            return Optional.of(new EdgeFact(
//                    subClassAxiom.getSubClass()
//                            .asOWLClass()
//                            .getIRI()
//                            .toString(),
//                    SUBCLASS_OF.toString(),
//                    subClassAxiom.getSuperClass()
//                            .asOWLClass()
//                            .getIRI()
//                            .toString(),
//                    axiom
//            ));
//        }
//
//        return Optional.empty();
//    }
}
