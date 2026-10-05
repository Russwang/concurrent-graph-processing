package uk.ac.qub.csc3021.graph;

import java.util.Collections;

public class ParallelContextSingleThread extends ParallelContext {
    public ParallelContextSingleThread() {
	// We only use one thread in this case
	super( 1 );
    }

    // Call into the iterate method and visit all edges
    public void edgemap( SparseMatrix matrix, Relax relax ) {
        Relax guardedRelax = prepareRelax(matrix, relax, false);
        execute(Collections.singletonList(() -> matrix.edgemap(guardedRelax)));
    }
}
