package uk.ac.qub.csc3021.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Partition the matrix's vertex domain into disjoint, balanced ranges.
public class ParallelContextSimple extends ParallelContext {
    public ParallelContextSimple(int num_threads_) {
        super(num_threads_);
    }

    @Override
    public void edgemap(SparseMatrix matrix, Relax relax) {
        Objects.requireNonNull(matrix, "matrix");
        int numVertices = matrix.getNumVertices();
        int numThreads = Math.min(getNumThreads(), numVertices);
        Relax guardedRelax = prepareRelax(matrix, relax, numThreads > 1);
        List<Runnable> actions = new ArrayList<>();
        if (numThreads > 0) {
            int divide = numVertices / numThreads;
            int remainder = numVertices % numThreads;
            int start = 0;
            for (int i = 0; i < numThreads; ++i) {
                final int from = start;
                final int to = from + divide + (i < remainder ? 1 : 0);
                actions.add(() -> matrix.ranged_edgemap(guardedRelax, from, to));
                start = to;
            }
        }
        execute(actions);
    }
}
