package uk.ac.qub.csc3021.graph;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Dependency-free regression tests with independent numerical/BFS oracles. */
public final class GraphRegressionTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("graph-regression-");
        PrintStream original = System.err;
        try (PrintStream quiet = new PrintStream(OutputStream.nullOutputStream())) {
            System.setErr(quiet);
            verifyGraph(temp, 0, new int[0][2], true);
            verifyGraph(temp, 5, new int[0][2], true);
            verifyGraph(temp, 1, new int[][]{{0, 0}, {0, 0}}, true);
            verifyGraph(temp, 6, new int[][]{
                    {0, 1}, {1, 0}, {1, 2}, {2, 1}, {3, 4}, {4, 3}}, true);
            verifyGraph(temp, 8, new int[][]{
                    {7, 0}, {1, 0}, {2, 0}, {2, 0}, {0, 3}, {3, 1}, {4, 4}}, false);
            Random random = new Random(3021);
            List<int[]> dense = new ArrayList<>();
            for (int i = 0; i < 350; i++) {
                dense.add(new int[]{random.nextInt(24), random.nextInt(24)});
            }
            verifyGraph(temp, 24, dense.toArray(new int[0][]), false);
            List<int[]> symmetric = new ArrayList<>();
            for (int[] edge : dense) {
                symmetric.add(edge);
                symmetric.add(new int[]{edge[1], edge[0]});
            }
            verifyGraph(temp, 24, symmetric.toArray(new int[0][]), true);
            List<int[]> path = new ArrayList<>();
            for (int i = 0; i < 129; i++) {
                path.add(new int[]{i, i + 1});
                path.add(new int[]{i + 1, i});
            }
            SparseMatrix longPath = writeMatrix(temp, "CSC", 130, path.toArray(new int[0][]));
            for (ParallelContext context : new ParallelContext[]{
                    new ParallelContextSingleThread(), new ParallelContextSimple(4),
                    new SparseMatrixPipelined(4)}) {
                ParallelContextHolder.set(context);
                try {
                    equal(new int[]{130}, ConnectedComponents.compute(longPath), "long-path CC");
                    equal(new int[]{130}, DisjointSetCC.compute(longPath), "long-path DS");
                } finally {
                    context.terminate();
                }
            }
            verifyMalformed(temp);
            verifyWorkerLifecycle(temp);
            verifySaturatedPipeline(temp);
            verifyFinalEdgeInterruption(temp);
        } finally {
            System.setErr(original);
            try (java.util.stream.Stream<Path> files = Files.walk(temp)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toArray(Path[]::new)) {
                    Files.delete(file);
                }
            }
        }
        System.out.println("Graph regression tests passed (" + checks + " checks)");
    }

    private static void verifyGraph(Path temp, int n, int[][] edges, boolean undirected) throws Exception {
        double[] ranks = referencePageRank(n, edges);
        int[] components = referenceComponents(n, edges);
        List<Supplier<ParallelContext>> contexts = Arrays.asList(
                ParallelContextSingleThread::new,
                () -> new ParallelContextSimple(1), () -> new ParallelContextSimple(4),
                () -> new SparseMatrixPipelined(1), () -> new SparseMatrixPipelined(2),
                () -> new SparseMatrixPipelined(4));
        for (String format : new String[]{"COO", "CSR", "CSC"}) {
            SparseMatrix matrix = writeMatrix(temp, format, n, edges);
            require(matrix.getNumVertices() == n && matrix.getNumEdges() == edges.length, "graph counts");
            int[] expectedDegree = new int[n];
            for (int[] edge : edges) expectedDegree[edge[0]]++;
            int[] degree = new int[n];
            Arrays.fill(degree, 99);
            matrix.calculateOutDegree(degree);
            equal(expectedDegree, degree, "outdegree overwrites existing values");
            matrix.calculateOutDegree(degree);
            equal(expectedDegree, degree, "repeated outdegree");
            Map<Long, Integer> expected = new HashMap<>();
            for (int[] edge : edges) addEdge(expected, edge[0], edge[1]);
            Map<Long, Integer> actual = new HashMap<>();
            matrix.edgemap((src, dst) -> addEdge(actual, src, dst));
            require(expected.equals(actual), "all edges including duplicates");
            actual.clear();
            for (int i = 0; i < n; i++) {
                matrix.ranged_edgemap((src, dst) -> addEdge(actual, src, dst), i, i + 1);
            }
            matrix.ranged_edgemap((src, dst) -> { throw new AssertionError("empty range"); }, n, n);
            require(expected.equals(actual), "vertex partitions visit each edge once");
            for (Supplier<ParallelContext> factory : contexts) {
                ParallelContext context = factory.get();
                ParallelContextHolder.set(context);
                try {
                    close(ranks, PageRank.compute(matrix), "PageRank vs independent oracle");
                    equal(components, DisjointSetCC.compute(matrix), "DS vs BFS");
                    if (undirected) equal(components, ConnectedComponents.compute(matrix), "CC vs BFS");
                } finally {
                    context.terminate();
                }
            }
        }
    }

    private static SparseMatrix writeMatrix(Path temp, String format, int n, int[][] edges) throws Exception {
        StringBuilder text = new StringBuilder(format + "\n" + n + "\n" + edges.length + "\n");
        if (format.equals("COO")) {
            for (int[] edge : edges) text.append(edge[0]).append('\t').append(edge[1]).append('\n');
        } else {
            for (int vertex = 0; vertex < n; vertex++) {
                text.append(vertex);
                for (int[] edge : edges) {
                    int owner = format.equals("CSR") ? edge[0] : edge[1];
                    if (owner == vertex) text.append("  ").append(format.equals("CSR") ? edge[1] : edge[0]);
                }
                text.append('\n');
            }
        }
        Path file = temp.resolve("graph." + format.toLowerCase());
        Files.writeString(file, text);
        return read(file, format);
    }

    private static SparseMatrix read(Path file, String format) {
        switch (format) {
            case "COO": return new SparseMatrixCOO(file.toString());
            case "CSR": return new SparseMatrixCSR(file.toString());
            default: return new SparseMatrixCSC(file.toString());
        }
    }

    private static void verifyMalformed(Path temp) throws Exception {
        for (String format : new String[]{"COO", "CSR", "CSC"}) {
            Path file = temp.resolve("bad." + format.toLowerCase());
            for (String data : new String[]{"", "WRONG\n0\n0\n", format + "\n-1\n0\n",
                    format + "\n2\n1\n", format + "\n1\n0\n0 0\n",
                    format + "\n1\n1\n0 1\n", format + "\n1\n1\n0 -1\n",
                    format + "\n1\n0\n0\nextra\n"}) {
                Files.writeString(file, data);
                expectFailure(() -> read(file, format), "malformed " + format);
            }
            String body = format.equals("COO") ? "0\t1\n" : "0" + (format.equals("CSR") ? " 1" : "")
                    + "\n1" + (format.equals("CSC") ? " 0" : "") + "\n";
            Files.writeString(file, "  " + format.toLowerCase() + "  \n2 1\n" + body + "\n");
            SparseMatrix matrix = read(file, format);
            require(matrix.getNumEdges() == 1, "whitespace and combined counts");
            expectFailure(() -> matrix.ranged_edgemap((s, d) -> {}, -1, 1), "negative range");
            expectFailure(() -> matrix.ranged_edgemap((s, d) -> {}, 2, 1), "reversed range");
            expectFailure(() -> matrix.ranged_edgemap((s, d) -> {}, 0, 3), "out of bounds range");
            expectFailure(() -> matrix.calculateOutDegree(new int[1]), "short degree array");
        }
        expectFailure(() -> new ParallelContextSimple(0), "zero threads");
        expectFailure(() -> new SparseMatrixPipelined(-1), "negative threads");
        expectFailure(() -> new SparseMatrixCSC(temp.resolve("missing.csc").toString()), "missing input");
    }

    private static void verifyWorkerLifecycle(Path temp) throws Exception {
        SparseMatrix matrix = writeMatrix(temp, "CSC", 8,
                new int[][]{{0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}, {0, 6}, {0, 7}});
        for (ParallelContext context : new ParallelContext[]{new ParallelContextSimple(4), new SparseMatrixPipelined(4)}) {
            RuntimeException sentinel = new IllegalStateException("worker failure");
            try {
                context.edgemap(matrix, (src, dst) -> { throw sentinel; });
                throw new AssertionError("worker exception was swallowed");
            } catch (RuntimeException e) {
                require(e == sentinel, "original worker exception propagates");
            }
            for (int pass = 0; pass < 3; pass++) {
                java.util.concurrent.atomic.AtomicInteger visits = new java.util.concurrent.atomic.AtomicInteger();
                context.edgemap(matrix, (src, dst) -> visits.incrementAndGet());
                require(visits.get() == 7, "reuse after failure and repeated sweeps");
            }
            context.terminate();
            expectFailure(() -> context.edgemap(matrix, (src, dst) -> {}), "closed context");
        }
        for (ParallelContext context : new ParallelContext[]{new ParallelContextSimple(2), new SparseMatrixPipelined(3)}) {
            CountDownLatch started = new CountDownLatch(1);
            AtomicReference<Throwable> outcome = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                try {
                    context.edgemap(matrix, (src, dst) -> {
                        started.countDown();
                        try { Thread.sleep(10000); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    });
                    outcome.set(new AssertionError("interrupted sweep returned normally"));
                } catch (RuntimeException e) {
                    if (!Thread.currentThread().isInterrupted()) outcome.set(new AssertionError("interrupt flag lost"));
                }
            });
            caller.setDaemon(true);
            caller.start();
            require(started.await(3, TimeUnit.SECONDS), "worker started");
            caller.interrupt();
            caller.join(3000);
            require(!caller.isAlive(), "interrupted sweep stops promptly");
            require(outcome.get() == null, "interruption propagates and flag restored");
            context.terminate();
        }
    }

    private static void verifySaturatedPipeline(Path temp) throws Exception {
        SparseMatrix matrix = writeMatrix(temp, "CSC", 5000, new int[][]{{0, 0}});
        ParallelContext context = new SparseMatrixPipelined(2);
        RuntimeException sentinel = new IllegalStateException("consumer failed with producer blocked");
        long start = System.nanoTime();
        try {
            try {
                context.edgemap(matrix, (src, dst) -> {
                    // Give the producer time to fill the 1024-slot queue.
                    try { Thread.sleep(100); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    throw sentinel;
                });
                throw new AssertionError("consumer failure was swallowed");
            } catch (RuntimeException e) {
                require(e == sentinel, "saturated pipeline preserves original failure");
            }
            require(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(3), "blocked producer cancels promptly");
            java.util.concurrent.atomic.AtomicInteger visits = new java.util.concurrent.atomic.AtomicInteger();
            context.edgemap(matrix, (src, dst) -> visits.incrementAndGet());
            require(visits.get() == 1, "saturated pipeline can recover without leftover tasks");
        } finally {
            context.terminate();
        }
    }

    private static void verifyFinalEdgeInterruption(Path temp) throws Exception {
        SparseMatrix matrix = writeMatrix(temp, "CSC", 1, new int[][]{{0, 0}});
        for (ParallelContext context : new ParallelContext[]{new ParallelContextSingleThread(),
                new ParallelContextSimple(1), new ParallelContextSimple(8), new SparseMatrixPipelined(1)}) {
            AtomicReference<Throwable> outcome = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                try {
                    context.edgemap(matrix, (src, dst) -> Thread.currentThread().interrupt());
                    outcome.set(new AssertionError("last-edge interruption was swallowed"));
                } catch (RuntimeException e) {
                    if (!Thread.currentThread().isInterrupted()) outcome.set(new AssertionError("interrupt flag lost"));
                }
            });
            caller.setDaemon(true);
            caller.start();
            caller.join(3000);
            require(!caller.isAlive() && outcome.get() == null, "last-edge cancellation and interrupt flag");
            context.terminate();
        }
    }

    private static double[] referencePageRank(int n, int[][] edges) {
        if (n == 0) return new double[0];
        int[] degree = new int[n];
        for (int[] edge : edges) degree[edge[0]]++;
        double[] rank = new double[n];
        Arrays.fill(rank, 1.0 / n);
        for (int iteration = 0; iteration < 10000; iteration++) {
            double dangling = 0;
            for (int i = 0; i < n; i++) if (degree[i] == 0) dangling += rank[i];
            double[] next = new double[n];
            Arrays.fill(next, (0.15 + 0.85 * dangling) / n);
            for (int[] edge : edges) next[edge[1]] += 0.85 * rank[edge[0]] / degree[edge[0]];
            double residual = 0;
            for (int i = 0; i < n; i++) residual += Math.abs(next[i] - rank[i]);
            rank = next;
            if (residual < 1e-12) return rank;
        }
        throw new AssertionError("reference PageRank did not converge");
    }

    private static int[] referenceComponents(int n, int[][] edges) {
        List<List<Integer>> neighbors = new ArrayList<>();
        for (int i = 0; i < n; i++) neighbors.add(new ArrayList<>());
        for (int[] edge : edges) {
            neighbors.get(edge[0]).add(edge[1]);
            neighbors.get(edge[1]).add(edge[0]);
        }
        boolean[] visited = new boolean[n];
        List<Integer> sizes = new ArrayList<>();
        for (int vertex = 0; vertex < n; vertex++) {
            if (visited[vertex]) continue;
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            pending.add(vertex);
            visited[vertex] = true;
            int size = 0;
            while (!pending.isEmpty()) {
                int current = pending.remove();
                size++;
                for (int neighbor : neighbors.get(current)) {
                    if (!visited[neighbor]) {
                        visited[neighbor] = true;
                        pending.add(neighbor);
                    }
                }
            }
            sizes.add(size);
        }
        return sizes.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void addEdge(Map<Long, Integer> edges, int src, int dst) {
        long edge = ((long) src << 32) | (dst & 0xffffffffL);
        edges.merge(edge, 1, Integer::sum);
    }

    private static void close(double[] expected, double[] actual, String message) {
        require(expected.length == actual.length, message + " length");
        double mass = 0;
        for (int i = 0; i < actual.length; i++) {
            require(Double.isFinite(actual[i]) && actual[i] >= 0
                    && Math.abs(expected[i] - actual[i]) < 5e-7, message + " vertex " + i);
            mass += actual[i];
        }
        if (actual.length > 0) require(Math.abs(mass - 1) < 1e-12, "PageRank probability mass");
    }

    private static void equal(int[] expected, int[] actual, String message) {
        require(Arrays.equals(expected, actual), message + ": expected " + Arrays.toString(expected)
                + ", got " + Arrays.toString(actual));
    }

    private static void expectFailure(Runnable action, String message) {
        try { action.run(); }
        catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError("Expected failure: " + message);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
