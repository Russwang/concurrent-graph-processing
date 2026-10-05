package uk.ac.qub.csc3021.graph;

import java.util.Objects;

// This class holds a single instance of a parallel context
public class ParallelContextHolder {
    private static volatile ParallelContext context = null;

    private ParallelContextHolder() { }

    public static void set( ParallelContext context_ ) {
        context = Objects.requireNonNull(context_, "context");
    }

    public static ParallelContext get() {
        ParallelContext current = context;
        if (current == null) {
            throw new IllegalStateException("Set a parallel context before running a graph algorithm");
        }
        return current;
    }
}
