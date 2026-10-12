package cz.cvut.fel.ontodiff.generators;

import cz.cvut.fel.ontodiff.changes.ChangeCandidate;
import cz.cvut.fel.ontodiff.DiffResult;

import java.util.stream.Stream;

public interface CandidateGenerator {

    Stream<ChangeCandidate> generate(DiffResult diff);

}
