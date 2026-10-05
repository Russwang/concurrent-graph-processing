package uk.ac.qub.csc3021.graph;

// Label propagation for undirected graphs: store each edge in both directions.
public class ConnectedComponents {
    private static class CCRelax implements Relax {
        private final int[] current;
        private final int[] next;

        CCRelax(int[] current, int[] next) {
            this.current = current;
            this.next = next;
        }

        public void relax(int src, int dst) {
            // Read the previous iteration only. Each destination is protected
            // by the parallel context or owned by one CSC worker.
            next[dst] = Math.min(next[dst], current[src]);
        }
    }

    public static int[] compute(SparseMatrix matrix) {
        int n = matrix.getNumVertices();
        int[] current = new int[n];
        int[] next = new int[n];
        for (int i = 0; i < n; i++) {
            current[i] = next[i] = i;
        }

        CCRelax relax = new CCRelax(current, next);
        ParallelContext context = ParallelContextHolder.get();
        boolean changed = n > 0;
        int iterations = 0;
        long start = System.nanoTime();
        // Labels decrease monotonically. Run until convergence, including for
        // graphs whose diameter exceeds the old 100-iteration cutoff.
        while (changed) {
            context.edgemap(matrix, relax);
            changed = false;
            for (int i = 0; i < n; i++) {
                if (current[i] != next[i]) {
                    current[i] = next[i];
                    changed = true;
                }
            }
            iterations++;
        }

        int[] remap = new int[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (current[i] == i) {
                remap[i] = count++;
            }
        }
        int[] sizes = new int[count];
        for (int label : current) {
            sizes[remap[label]]++;
        }
        System.err.println("ConnectedComponents: " + count + " components, "
                + iterations + " iterations, "
                + (System.nanoTime() - start) * 1e-9 + " seconds");
        return sizes;
    }
}
