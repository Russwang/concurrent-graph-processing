package uk.ac.qub.csc3021.graph;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

// Compressed sparse columns: each row lists a destination vertex's sources.
public class SparseMatrixCSC extends SparseMatrix {
    int[] index;
    int[] source;
    int num_vertices;
    int num_edges;

    public SparseMatrixCSC(String file) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(file), StandardCharsets.UTF_8)) {
            readFile(reader);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read CSC graph '" + file
                    + "': " + exception.getMessage(), exception);
        }
    }

    public void readFile(BufferedReader reader) throws IOException {
        GraphFileReader.Data graph = GraphFileReader.read(reader, "CSC");
        num_vertices = graph.vertices;
        num_edges = graph.edges;
        index = graph.index;
        source = graph.neighbours;
    }

    public int getNumVertices() { return num_vertices; }

    public int getNumEdges() { return num_edges; }

    @Override
    public boolean isDestinationPartitioned() { return true; }

    public void calculateOutDegree(int[] outdeg) {
        checkOutDegreeArray(outdeg);
        Arrays.fill(outdeg, 0, num_vertices, 0);
        for (int edge = 0; edge < num_edges; edge++) {
            outdeg[source[edge]]++;
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
        for (int destination = from; destination < to; destination++) {
            for (int edge = index[destination]; edge < index[destination + 1]; edge++) {
                relax.relax(source[edge], destination);
            }
        }
    }
}
