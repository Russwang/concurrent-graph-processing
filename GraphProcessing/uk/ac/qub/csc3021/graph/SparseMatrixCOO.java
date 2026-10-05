package uk.ac.qub.csc3021.graph;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

// Coordinate format. The edge arrays preserve the input edge order.
public class SparseMatrixCOO extends SparseMatrix {
    int[] sources;
    int[] destinations;
    int num_vertices;
    int num_edges;
    private int[] sourceIndex;
    private int[] edgesBySource;

    public SparseMatrixCOO(String file) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(file), StandardCharsets.UTF_8)) {
            readFile(reader);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read COO graph '" + file
                    + "': " + exception.getMessage(), exception);
        }
    }

    void readFile(BufferedReader reader) throws IOException {
        GraphFileReader.Data graph = GraphFileReader.read(reader, "COO");
        int[] offsets = new int[graph.vertices + 1];
        for (int source : graph.sources) {
            offsets[source + 1]++;
        }
        for (int vertex = 1; vertex <= graph.vertices; vertex++) {
            offsets[vertex] += offsets[vertex - 1];
        }
        int[] next = offsets.clone();
        int[] edgeOrder = new int[graph.edges];
        for (int edge = 0; edge < graph.edges; edge++) {
            edgeOrder[next[graph.sources[edge]]++] = edge;
        }
        num_vertices = graph.vertices;
        num_edges = graph.edges;
        sources = graph.sources;
        destinations = graph.neighbours;
        sourceIndex = offsets;
        edgesBySource = edgeOrder;
    }

    public int getNumVertices() { return num_vertices; }

    public int getNumEdges() { return num_edges; }

    public void calculateOutDegree(int[] outdeg) {
        checkOutDegreeArray(outdeg);
        for (int vertex = 0; vertex < num_vertices; vertex++) {
            outdeg[vertex] = sourceIndex[vertex + 1] - sourceIndex[vertex];
        }
    }

    public int getEdgesForVertex(int vertex) {
        checkVertex(vertex);
        return sourceIndex[vertex + 1] - sourceIndex[vertex];
    }

    public void edgemap(Relax relax) {
        for (int edge = 0; edge < num_edges; edge++) {
            relax.relax(sources[edge], destinations[edge]);
        }
    }

    public void ranged_edgemap(Relax relax, int from, int to) {
        checkRange(from, to);
        for (int position = sourceIndex[from]; position < sourceIndex[to]; position++) {
            int edge = edgesBySource[position];
            relax.relax(sources[edge], destinations[edge]);
        }
    }
}
