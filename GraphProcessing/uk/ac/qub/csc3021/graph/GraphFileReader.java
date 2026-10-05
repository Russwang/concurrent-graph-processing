package uk.ac.qub.csc3021.graph;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.StringTokenizer;

/** Shared, streaming validation for the course graph text formats. */
final class GraphFileReader {
    static final class Data {
        final int vertices;
        final int edges;
        final int[] index;
        final int[] sources;
        final int[] neighbours;

        Data(int vertices, int edges, int[] index, int[] sources, int[] neighbours) {
            this.vertices = vertices;
            this.edges = edges;
            this.index = index;
            this.sources = sources;
            this.neighbours = neighbours;
        }
    }

    private final BufferedReader reader;
    private int lineNumber;

    private GraphFileReader(BufferedReader reader) {
        this.reader = reader;
    }

    static Data read(BufferedReader reader, String format) throws IOException {
        return new GraphFileReader(reader).read(format);
    }

    private Data read(String format) throws IOException {
        String header = requiredLine();
        if (header.startsWith("\uFEFF")) {
            header = header.substring(1).trim();
        }
        boolean compressed = !format.equals("COO");
        if (!header.equalsIgnoreCase(format)
                && !(compressed && header.equalsIgnoreCase("CSC-CSR"))) {
            throw error("expected " + format + " header, found '" + header + "'");
        }

        StringTokenizer counts = new StringTokenizer(requiredLine());
        int vertices;
        int edges;
        if (counts.countTokens() == 2) {
            vertices = nonNegativeInteger(counts.nextToken(), "vertex count");
            edges = nonNegativeInteger(counts.nextToken(), "edge count");
        } else if (counts.countTokens() == 1) {
            vertices = nonNegativeInteger(counts.nextToken(), "vertex count");
            StringTokenizer edgeCount = new StringTokenizer(requiredLine());
            if (edgeCount.countTokens() != 1) {
                throw error("expected one edge count");
            }
            edges = nonNegativeInteger(edgeCount.nextToken(), "edge count");
        } else {
            throw error("expected a vertex count or vertex and edge counts");
        }
        if (vertices == Integer.MAX_VALUE) {
            throw error("vertex count is too large for a Java array index");
        }
        if (vertices == 0 && edges != 0) {
            throw error("an empty graph cannot contain edges");
        }

        int[] neighbours = new int[edges];
        int[] sources = compressed ? null : new int[edges];
        int[] index = compressed ? new int[vertices + 1] : null;
        int position = 0;
        if (compressed) {
            for (int vertex = 0; vertex < vertices; vertex++) {
                StringTokenizer row = new StringTokenizer(requiredLine());
                if (!row.hasMoreTokens()) {
                    throw error("expected an adjacency row for vertex " + vertex);
                }
                int rowVertex = integer(row.nextToken(), "row vertex");
                if (rowVertex != vertex) {
                    throw error("expected row vertex " + vertex + ", found " + rowVertex);
                }
                index[vertex] = position;
                while (row.hasMoreTokens()) {
                    if (position == edges) {
                        throw error("more edges than the declared count " + edges);
                    }
                    neighbours[position++] = vertexId(row.nextToken(), vertices);
                }
            }
            index[vertices] = position;
            if (position != edges) {
                throw error("declared " + edges + " edges, found " + position);
            }
        } else {
            for (; position < edges; position++) {
                StringTokenizer edge = new StringTokenizer(requiredLine());
                if (edge.countTokens() != 2) {
                    throw error("expected exactly two vertex IDs for an edge");
                }
                sources[position] = vertexId(edge.nextToken(), vertices);
                neighbours[position] = vertexId(edge.nextToken(), vertices);
            }
        }

        String trailing;
        while ((trailing = reader.readLine()) != null) {
            lineNumber++;
            if (!trailing.trim().isEmpty()) {
                throw error("unexpected data after the declared graph");
            }
        }
        return new Data(vertices, edges, index, sources, neighbours);
    }

    private String requiredLine() throws IOException {
        lineNumber++;
        String line = reader.readLine();
        if (line == null) {
            throw error("premature end of file");
        }
        return line.trim();
    }

    private int vertexId(String value, int vertices) throws IOException {
        int vertex = integer(value, "vertex ID");
        if (vertex < 0 || vertex >= vertices) {
            throw error("vertex ID " + vertex + " is outside [0, " + vertices + ")");
        }
        return vertex;
    }

    private int nonNegativeInteger(String value, String description) throws IOException {
        int result = integer(value, description);
        if (result < 0) {
            throw error(description + " must be non-negative");
        }
        return result;
    }

    private int integer(String value, String description) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw error("invalid " + description + ": '" + value + "'");
        }
    }

    private IOException error(String message) {
        return new IOException("line " + lineNumber + ": " + message);
    }
}
