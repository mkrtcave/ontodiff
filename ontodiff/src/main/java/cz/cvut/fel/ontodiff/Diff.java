package cz.cvut.fel.ontodiff;

import cz.cvut.fel.ontodiff.generators.CandidateGenerator;
import cz.cvut.fel.ontodiff.generators.NodeCreationCandidateGenerator;
import cz.cvut.fel.ontodiff.generators.NodeMoveCandidateGenerator;
import cz.cvut.fel.ontodiff.service.HighLevelDiffServiceImpl;
import cz.cvut.fel.ontodiff.service.OWLDiffOntologyDiffService;
import cz.cvut.fel.ontodiff.service.OntologyDiffService;
import cz.cvut.fel.ontodiff.statesearch.SearchState;
import cz.cvut.fel.ontodiff.statesearch.StateSpaceSearch;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.IRIDocumentSource;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;

public class Diff {

    public Diff() {
    }
    public void diff(){

        String u1 = "file:/D:/owldiff/ontodiff/src/test/resources/ontologies/node-move/node-move-v1.ttl";
        String u2 = "file:/D:/owldiff/ontodiff/src/test/resources/ontologies/node-move/node-move-v2.ttl";

        try {
            final OWLOntologyManager originalM = OWLManager.createOWLOntologyManager();
            final OWLOntologyManager updateM = OWLManager.createOWLOntologyManager();

            OWLOntology originalO = originalM.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(u1)));
            OWLOntology updateO = updateM.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(u2)));

            OntologyDiffService service = new OWLDiffOntologyDiffService();

            DiffResult diffResult = service.diff(originalO, updateO, Engine.SYNTACTIC);

            List<CandidateGenerator> generators = List.of(
                    new NodeMoveCandidateGenerator(),
                    new NodeCreationCandidateGenerator()
        //                    new PredicateChangeCandidateGenerator(),
//                    new NodeRenameCandidateGenerator()
            );

            List<ChangeCandidate> candidates = generators.stream()
                    .flatMap(generator -> generator.generate(diffResult))
                    .toList();

            StateSpaceSearch search = new StateSpaceSearch();

            SearchState result = search.search(diffResult, candidates);

            System.out.println("Goal" + result.isGoal());

            System.out.println("Cost: " + result.cost());

            for (ChangeCandidate selected : result.selectedCandidates()) {
                System.out.println(selected.change());
            }


            HighLevelDiffServiceImpl diffService = new HighLevelDiffServiceImpl();

//            HighLevelDiffService.HighLevelDiff highDiff = diffService.from(diffResult);

//            RdfDiffModelBuilder builder = new RdfDiffModelBuilderImpl();




//            RdfDiffModelBuilder.DiffRunMetadata metadata =
//                    new RdfDiffModelBuilder.DiffRunMetadata(
//                            "http://w3id.org/ontodiff/instance#DiffRun_2025_01_15",
//                            "http://w3id.org/ontodiff/instance#appolo_sv_v1",
//                            "http://w3id.org/ontodiff/instance#appolo_sv_v2",
//                            "http://w3id.org/ontodiff/instance#DiffRun_2025_01_15",
//                            "ontodiff-cli",
//                            "1.0.0",
//                            java.time.Instant.now()
//                    );
//
//            Dataset dataset = builder.build(highDiff, metadata, "http://w3id.org/ontodiff/instance#DiffRun_2025_01_15");
////            RDFDataMgr.write(System.out, dataset, Lang.TURTLE);
//            GraphDbUploader uploader = new GraphDbUploader(
//                    "http://osw.felk.cvut.cz:7200/repositories/9999/statements",
//                    "http://w3id.org/ontodiff/instance#DiffRun_2025_01_15",
//                    null,
//                    null
//            );

//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            RDFDataMgr.write(baos, dataset, Lang.TRIG);
//            byte[] trigData = baos.toByteArray();

//            uploader.upload(dataset);
//            System.out.println(dataset);
        } catch (OWLException e) {
            e.printStackTrace();
        }
    }
}
