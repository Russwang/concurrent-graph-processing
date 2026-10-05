package uk.ac.qub.csc3021.graph;

// Functional interface that describes the operation performed when visiting
// an edge.
public interface Relax {
    public void relax( int source, int destination );
}
