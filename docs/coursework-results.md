# Historical coursework measurements

Source: **CSC3021 Assignment3 Report**, December 2024, pp. 7–9, corroborated by screenshots in **Concurrent Report.docx**. The original reports remain private because they contain student and submission-account information.

The experiment ran PageRank on `orkut_undir.csc-csr` (an undirected Orkut graph) on a MacBook Pro. The report records 4 physical CPU cores and 8 logical CPUs, but omits the exact CPU model, RAM, JDK version and JVM settings. Times below are transcribed from the original terminal screenshots and rounded to three decimal places.

| Reported threads | Program total (s) | PageRank phase (s) | Iteration timing labeled “edgemap” (s) | Total speedup |
| ---: | ---: | ---: | ---: | ---: |
| 1 | 167.654 | 152.234 | 150.560 | 1.00× |
| 2 | 114.182 | 99.335 | 97.507 | 1.47× |
| 3 | 88.157 | 72.835 | 71.212 | 1.90× |
| 4 | 79.295 | 64.592 | 62.706 | 2.11× |
| 5 | 74.338 | 58.405 | 56.552 | 2.26× |
| 6 | 76.353 | 60.393 | 57.834 | 2.20× |
| 7 | 78.260 | 62.757 | 61.031 | 2.14× |
| 8 | 72.712 | 57.357 | 55.704 | 2.31× |
| 9 | 78.601 | 63.410 | 61.687 | 2.13× |
| 10 | 73.557 | 59.101 | 57.492 | 2.28× |

Speedup is `one-thread program time / selected program time`, using the same table's baseline. For example, `167.654 / 72.712 = 2.31`. The best observed total runtime in this experiment was at 8 reported threads. The screenshots end at iteration 58, with residual approximately `9.072e-8` and probability mass `1.0`.

These are single-run coursework observations. No warm-up policy, repeated trials or confidence intervals were recorded. They do not establish nearly linear scaling, a universally optimal thread count or a causal effect of hyperthreading. The old counter labeled “edgemap” includes iteration work outside edge traversal; the maintained code names this timing “PageRank iterations.”

The table appears in the report’s static-partitioning section and its commands use `ICHOOSE`. An earlier coursework checkpoint mapped that argument to static partitions; a later checkpoint mapped it to a pipeline with an additional producer beyond the requested consumers. The maintained version selects the pipeline with its producer included in the total thread budget. The report does not pin a code revision for each run, so historical thread labels should not be equated with a new `ICHOOSE` run.

The report also explored CSC input reader/parser queues (pp. 15–17). Those tests used different thread arguments and only one sample per queue size; they do not justify a universal queue capacity or a reliable input speedup. The maintained loader is sequential and validated, resolving the old producer/consumer error-handling hangs.

The dataset is not bundled. **No new Orkut performance benchmark was run during maintenance.** Current validation exercises correctness, failure handling and command-line behavior on reproducible generated and bundled graphs. A new performance study should pin the dataset and machine, record the JDK and heap settings, separate input/algorithm/output timing, warm up the JVM, repeat trials, and report timing distributions.
