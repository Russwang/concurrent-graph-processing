package uk.ac.qub.csc3021.graph;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

// Compressed sparse rows: each row lists a source vertex's destinations.
public class SparseMatrixCSR extends SparseMatrix {
    int[] index;
    int[] destination;
    int num_vertices;
    int num_edges;

    public SparseMatrixCSR(String file) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(file), StandardCharsets.UTF_8)) {
            readFile(reader);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read CSR graph '" + file
                    + "': " + exception.getMessage(), exception);
        }
    }

    void readFile(BufferedReader reader) throws IOException {
        GraphFileReader.Data graph = GraphFileReader.read(reader, "CSR");
        num_vertices = graph.vertices;
        num_edges = graph.edges;
        index = graph.index;
        destination = graph.neighbours;
    }

    public int getNumVertices() { return num_vertices; }

    public int getNumEdges() { return num_edges; }

    public void calculateOutDegree(int[] outdeg) {
        checkOutDegreeArray(outdeg);
        for (int vertex = 0; vertex < num_vertices; vertex++) {
            outdeg[vertex] = index[vertex + 1] - index[vertex];
        }
    }

    public int getEdgesForVertex(int vertex) {
        checkVertex(vertex);
        return index[vertex + 1] - index[vertex];
    }

    public void edgemap(Relax relax) {
        ranged_edgemap(relax, 0, num_vertices);
    }

    public void ranged_edgemap(Relax relax, int from, int to) {
        checkRange(from, to);
        for (int source = from; source < to; source++) {
            for (int edge = index[source]; edge < index[source + 1]; edge++) {
                relax.relax(source, destination[edge]);
            }
        }
    }
}
