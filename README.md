# Concurrent Graph Processing

A Java graph-processing project developed for **CSC3021 Concurrent Programming** at Queen's University Belfast (2024–25). It explores sparse graph representations, parallel PageRank and connected components, and producer–consumer scheduling.

## Features

- **COO, CSR and CSC** graph representations, with validated streaming input and vertex-range traversal.
- **PageRank** with damping factor 0.85, dangling-node redistribution and an L1 convergence tolerance of `1e-7`.
- **Connected components** through synchronous label propagation, plus a concurrent union-find implementation using atomic compare-and-set and path halving.
- Sequential traversal, static vertex partitions and a bounded producer–consumer pipeline.
- Dependency-free regression tests and GitHub Actions checks on Java 17 and 21.

## Historical coursework results

The original coursework report measured PageRank on the **undirected Orkut graph**, on a MacBook Pro with **4 physical cores and 8 logical CPUs**:

| Reported threads | Total runtime | Speedup against 1 thread |
| ---: | ---: | ---: |
| 1 | 167.654 s | 1.00× |
| 2 | 114.182 s | 1.47× |
| 4 | 79.295 s | 2.11× |
| 8 | 72.712 s | **2.31×** |

These are single-run **historical measurements**, transcribed from the original report, pp. 8–9. They have not been rerun on the repaired version. The full timing table, source details and limitations are in [the results notes](docs/coursework-results.md).

## Quick start

Requires **JDK 17+** and `make`. Tests also require Python 3; no external Java or Python libraries are needed.

```sh
cd GraphProcessing
make
java -ea -jar lib/graph.jar PR 4 /tmp/pagerank.txt CSC examples/components.csc
java -ea -jar lib/graph.jar CC 4 /tmp/components.txt ICHOOSE examples/components.csc
java -ea -jar lib/graph.jar DS 4 /tmp/disjoint-set.txt CSR examples/components.csr
make test
```

The sample graph has components of sizes **3, 2 and 1**. CC and DS output:

```text
0 3
1 2
2 1
```

You can also run `java -ea -cp build/classes Driver ...`. Run `make clean` to remove generated files, or `make submit` to create the source-only coursework ZIP.

## Command-line interface

```text
java -ea -jar lib/graph.jar ALGORITHM THREADS OUTPUT FORMAT INPUT [INPUT...]
```

| Argument | Values and meaning |
| --- | --- |
| `ALGORITHM` | `PR`: PageRank; `CC`: label propagation; `OPT`: legacy alias of CC; `DS`: atomic union-find |
| `THREADS` | Positive integer: total graph-execution thread budget |
| `OUTPUT` | UTF-8 output file; its parent directory must already exist |
| `FORMAT` | `COO`, `CSR`, `CSC`, or `ICHOOSE` |
| `INPUT` | Decompressed graph file; a single path may have any extension |

With COO/CSR/CSC, one thread selects sequential traversal and multiple threads select static partitions. `ICHOOSE` reads CSC and selects the pipeline: one thread executes sequentially; a larger budget includes **one producer and up to `THREADS - 1` consumers**. Workers are capped by the number of vertices. The budget excludes the waiting main thread and JVM runtime threads.

When multiple input paths are supplied, the driver selects the `.coo`, `.csr` or `.csc` file matching the requested format. `.csc-csr` can supply CSR or CSC for a symmetric graph. Algorithms and format arguments are case-insensitive.

PageRank writes `vertex_id rank` per line. CC and DS write `component_id size` per line, ordered by each component's smallest vertex. This lists individual component sizes rather than a size-frequency histogram. CC/OPT requires an **undirected graph stored with both directions of every edge**; it does not compute strongly connected components. DS treats directed edges as weak connectivity. Invalid arguments, malformed input and output failures produce a nonzero exit status with an error on stderr.

## Graph file format

The first line is `COO`, `CSR` or `CSC`. CSR and CSC also accept the shared header `CSC-CSR`. Then provide the vertex count and edge count on separate lines, or together as `V E` on one line. Vertex IDs are integers in `[0, V)`.

COO stores one `source destination` pair per edge:

```text
COO
3
2
0 1
1 2
```

CSR stores exactly one row per vertex in ascending ID order, listing outgoing neighbors:

```text
CSR
3
2
0 1
1 2
2
```

CSC lists incoming neighbors instead:

```text
CSC
3
2
0
1 0
2 1
```

Whitespace between numbers is flexible. Self-loops, duplicate edges, isolated vertices and empty graphs are supported; duplicate edges count toward the declared edge count. Blank lines are allowed only after the graph. The loader validates row order, IDs, edge counts, truncation and trailing data. The examples above are directed; use the supplied [sample graphs](GraphProcessing/examples) for CC.

## Parallel design and repairs

CSC partitions whole **destination vertices**. Each worker owns its output destinations during a sweep, so the PageRank and label-propagation callbacks can update them without locks. CSR and COO partition source vertices and protect shared destination updates with striped locks. The PageRank and label-propagation callbacks read immutable source values within a sweep; label propagation uses the previous iteration's snapshot.

The pipeline sends vertex tasks through a bounded 1024-slot queue. Each sweep owns a fresh queue, propagates worker failures and joins all workers before returning. Cancellation stops cooperative workers and preserves the caller's interrupt flag. A terminated context cannot be reused. The earlier threaded CSC reader was replaced with a validated streaming reader after finding hangs and swallowed parsing errors.

The maintenance pass also completed the previously stubbed DS algorithm, removed CC's 100-iteration cutoff (which produced incorrect answers for long paths), made the thread argument effective for all formats, and made CLI failures visible to scripts. PageRank returns only a converged result and fails if its 1000-iteration bound is reached. Generated class files, IDE files and macOS metadata are excluded from version control; the executable JAR includes inner classes.

`make test` compares results with independent PageRank and BFS references across formats and execution modes. It covers dangling nodes, duplicate edges, randomized directed and symmetric graphs, paths longer than 100 edges, empty graphs, malformed files, worker exceptions, a saturated pipeline queue, cancellation and CLI output behavior.

Static partitions balance vertex counts rather than edge counts; a single high-degree vertex can still dominate a worker. The pipeline introduces queue and thread-start overhead, so parallel execution is not guaranteed to be faster on small graphs. COO's source-range index uses additional `O(V + E)` memory. Large datasets are deliberately kept outside this repository.

## Structure and provenance

```text
GraphProcessing/
  Driver.java                     Command-line entry point
  Makefile                        Build, tests and coursework ZIP
  uk/ac/qub/csc3021/graph/         Matrices, algorithms and execution contexts
  examples/                       Small equivalent graphs in all formats
  tests/                          Java and CLI regression tests
docs/coursework-results.md         Historical measurements and limitations
.github/workflows/ci.yml           Java 17/21 checks
```

The course framework originated with **Hans Vandierendonck** and was updated by **Ivor Spence**. Coursework implementations and experiments were completed by **Xinghao Wang**; the later maintenance pass adds correctness fixes, tests and project documentation.

The original coursework repository and reports are retained privately. This repository starts from a cleaned snapshot because the old history included generated files, personal course-account information and a previously committed 1.79 GB dataset. No upstream license was supplied with the course framework; this repository does not assign a new license to that inherited code.
