import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import uk.ac.qub.csc3021.graph.*;

/** Command-line entry point for PageRank and connected components. */
public class Driver {
    private static final String USAGE =
            "Usage: java -ea Driver (PR|CC|OPT|DS) num-threads outputfile "
            + "(COO|CSR|CSC|ICHOOSE) inputfiles...";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IOException | RuntimeException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    private static void run(String[] args) throws IOException {
        if (args.length < 5) {
            throw new IllegalArgumentException(USAGE);
        }
        String algorithm = args[0].toUpperCase(Locale.ROOT);
        if (!algorithm.equals("PR") && !algorithm.equals("CC")
                && !algorithm.equals("OPT") && !algorithm.equals("DS")) {
            throw new IllegalArgumentException("Unknown algorithm '" + args[0] + "'. " + USAGE);
        }
        int threads;
        try {
            threads = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Number of threads must be a positive integer");
        }
        if (threads <= 0) {
            throw new IllegalArgumentException("Number of threads must be a positive integer");
        }
        String format = args[3].toUpperCase(Locale.ROOT);
        if (!format.equals("COO") && !format.equals("CSR")
                && !format.equals("CSC") && !format.equals("ICHOOSE")) {
            throw new IllegalArgumentException("Unknown format '" + args[3] + "'. " + USAGE);
        }
        String selected = format.equals("ICHOOSE") ? "CSC" : format;
        String input = selectInput(args, selected);
        long start = System.nanoTime();
        SparseMatrix matrix;
        switch (selected) {
            case "COO": matrix = new SparseMatrixCOO(input); break;
            case "CSR": matrix = new SparseMatrixCSR(input); break;
            default: matrix = new SparseMatrixCSC(input); break;
        }
        System.err.println("Input: " + input + ", format: " + format
                + ", vertices: " + matrix.getNumVertices()
                + ", edges: " + matrix.getNumEdges());
        System.err.println("Reading input: " + (System.nanoTime() - start) * 1e-9 + " seconds");

        ParallelContext context = format.equals("ICHOOSE")
                ? new SparseMatrixPipelined(threads)
                : threads == 1 ? new ParallelContextSingleThread()
                : new ParallelContextSimple(threads);
        ParallelContextHolder.set(context);
        System.err.println("Algorithm: " + algorithm + ", thread budget: " + threads);
        try {
            if (algorithm.equals("PR")) {
                writeToFile(args[2], PageRank.compute(matrix));
            } else if (algorithm.equals("DS")) {
                writeToFile(args[2], DisjointSetCC.compute(matrix));
            } else {
                writeToFile(args[2], ConnectedComponents.compute(matrix));
            }
        } finally {
            context.terminate();
        }
        System.err.println("Program total time: " + (System.nanoTime() - start) * 1e-9 + " seconds");
        System.err.println("All done");
    }

    private static String selectInput(String[] args, String format) {
        // A single path is selected directly; the parser checks its header
        // against the requested format.
        if (args.length == 5) {
            return args[4];
        }
        String selected = null;
        for (int i = 4; i < args.length; i++) {
            String name = args[i].toLowerCase(Locale.ROOT);
            if (name.endsWith("." + format.toLowerCase(Locale.ROOT))
                    || (!format.equals("COO") && name.endsWith(".csc-csr"))) {
                if (selected != null) {
                    throw new IllegalArgumentException("Multiple input files match " + format);
                }
                selected = args[i];
            }
        }
        if (selected == null) {
            throw new IllegalArgumentException("No input file matches " + format);
        }
        return selected;
    }

    static void writeToFile(String file, double[] values) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(file), StandardCharsets.UTF_8)) {
            for (int i = 0; i < values.length; i++) {
                writer.write(i + " " + values[i]);
                writer.newLine();
            }
        }
    }

    static void writeToFile(String file, int[] values) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(file), StandardCharsets.UTF_8)) {
            for (int i = 0; i < values.length; i++) {
                writer.write(i + " " + values[i]);
                writer.newLine();
            }
        }
    }
}
