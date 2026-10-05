package uk.ac.qub.csc3021.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// Dynamic vertex scheduling with a bounded producer-consumer queue.
// The requested thread count includes the producer; one thread runs serially.
public class SparseMatrixPipelined extends ParallelContext {
    private static final int END = -1;
    private static final int QUEUE_CAPACITY = 1024;

    public SparseMatrixPipelined(int num_threads_) {
        super(num_threads_);
    }

    @Override
    public void edgemap(SparseMatrix matrix, Relax relax) {
        Objects.requireNonNull(matrix, "matrix");
        int numVertices = matrix.getNumVertices();
        int consumers = Math.min(getNumThreads() - 1, numVertices);
        Relax guardedRelax = prepareRelax(matrix, relax, consumers > 1);
        if (consumers == 0) {
            execute(Collections.singletonList(() -> matrix.edgemap(guardedRelax)));
            return;
        }

        // A separate queue for each sweep prevents unfinished tasks/sentinels
        // from a failed traversal leaking into the following traversal.
        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        List<Runnable> actions = new ArrayList<>();
        actions.add(() -> {
            try {
                for (int vertex = 0; vertex < numVertices; ++vertex) {
                    queue.put(vertex);
                }
                for (int i = 0; i < consumers; ++i) {
                    queue.put(END);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Pipeline producer was interrupted", e);
            }
        });
        for (int i = 0; i < consumers; ++i) {
            actions.add(() -> {
                try {
                    while (true) {
                        int vertex = queue.take();
                        if (vertex == END) {
                            return;
                        }
                        matrix.ranged_edgemap(guardedRelax, vertex, vertex + 1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Pipeline consumer was interrupted", e);
                }
            });
        }
        execute(actions);
    }
}
