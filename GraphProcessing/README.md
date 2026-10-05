# GraphProcessing

Java source, build scripts and regression tests for the CSC3021 concurrent graph-processing project. See the [project README](../README.md) for features, historical results, graph formats and design details.

```sh
make
java -ea -jar lib/graph.jar PR 4 /tmp/pagerank.txt CSC examples/components.csc
java -ea -jar lib/graph.jar CC 4 /tmp/components.txt ICHOOSE examples/components.csc
make test
```

Requires JDK 17+, make, and Python 3 for CLI tests. The example's component sizes are 3, 2 and 1. `make clean` removes generated files; `make submit` packages only the Java sources for coursework submission.
