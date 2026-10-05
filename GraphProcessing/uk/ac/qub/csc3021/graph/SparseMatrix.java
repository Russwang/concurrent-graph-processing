package uk.ac.qub.csc3021.graph;

public abstract class SparseMatrix {
    // Return number of vertices in the graph
    public abstract int getNumVertices();

    // Return number of edges in the graph
    public abstract int getNumEdges();

    // Auxiliary in preparation of PageRank iteration: pre-calculate the
    // out-degree (number of outgoing edges) for each vertex
    public abstract void calculateOutDegree( int outdeg[] );

    // Number of edges visited by ranged_edgemap for this vertex.
    public abstract int getEdgesForVertex(int vertex);

    // True when disjoint vertex ranges write to disjoint destinations. This
    // allows destination-only relaxations to execute without additional locks.
    public boolean isDestinationPartitioned() { return false; }

    // Perform one sweep over all edges in the graph, calling the functional
    // interface Relax once for each edge.
    public abstract void edgemap( Relax relax );

    // Visit edges for vertices in the half-open interval [from, to). COO and
    // CSR partition by source vertex; CSC partitions by destination vertex.
    public abstract void ranged_edgemap( Relax relax, int from, int to );

    protected final void checkVertex(int vertex) {
        if (vertex < 0 || vertex >= getNumVertices()) {
            throw new IndexOutOfBoundsException("Vertex out of range: " + vertex);
        }
    }

    protected final void checkRange(int from, int to) {
        if (from < 0 || from > to || to > getNumVertices()) {
            throw new IndexOutOfBoundsException("Invalid vertex range [" + from
                    + ", " + to + ") for " + getNumVertices() + " vertices");
        }
    }

    protected final void checkOutDegreeArray(int[] outdeg) {
        if (outdeg == null || outdeg.length < getNumVertices()) {
            throw new IllegalArgumentException("Out-degree array must have at least "
                    + getNumVertices() + " entries");
        }
    }
}
