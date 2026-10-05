package uk.ac.qub.csc3021.graph;

import java.util.concurrent.atomic.AtomicIntegerArray;

// Parallel union-find. Directed input is treated as weak connectivity.
public class DisjointSetCC {
    private static class DSCCRelax implements Relax {
        private final AtomicIntegerArray parent;

        DSCCRelax(AtomicIntegerArray parent) {
            this.parent = parent;
        }

        public void relax(int src, int dst) {
            union(src, dst);
        }

        int find(int vertex) {
            int next;
            while ((next = parent.get(vertex)) != vertex) {
                // Path halving uses CAS so it cannot overwrite a newer link.
                parent.compareAndSet(vertex, next, parent.get(next));
                vertex = next;
            }
            return vertex;
        }

        private void union(int src, int dst) {
            while (true) {
                int left = find(src);
                int right = find(dst);
                if (left == right) {
                    return;
                }
                int higher = Math.max(left, right);
                int lower = Math.min(left, right);
                // Links always decrease, preventing cycles even when another
                // thread concurrently changes one of these roots.
                if (parent.compareAndSet(higher, higher, lower)) {
                    return;
                }
            }
        }
    }

    public static int[] compute(SparseMatrix matrix) {
        long start = System.nanoTime();
        int n = matrix.getNumVertices();
        AtomicIntegerArray parent = new AtomicIntegerArray(n);
        for (int i = 0; i < n; i++) {
            parent.set(i, i);
        }
        DSCCRelax relax = new DSCCRelax(parent);
        ParallelContextHolder.get().edgemap(matrix, relax);

        int[] remap = new int[n];
        int count = 0;
        for (int i = 0; i < n; i++) {
            if (relax.find(i) == i) {
                remap[i] = count++;
            }
        }
        int[] sizes = new int[count];
        for (int i = 0; i < n; i++) {
            sizes[remap[relax.find(i)]]++;
        }
        System.err.println("DisjointSetCC: " + count + " components, "
                + (System.nanoTime() - start) * 1e-9 + " seconds");
        return sizes;
    }
}
