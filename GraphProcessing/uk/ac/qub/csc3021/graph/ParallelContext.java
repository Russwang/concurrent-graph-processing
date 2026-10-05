package uk.ac.qub.csc3021.graph;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ParallelContext {
    private final int num_threads;
    private final Object lifecycle = new Object();
    private volatile boolean terminated;
    private Execution active;

    private static final class Execution {
        final Thread caller = Thread.currentThread();
        final Thread[] workers;
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final CountDownLatch complete = new CountDownLatch(1);

        Execution(int workerCount) {
            workers = new Thread[workerCount];
        }

        void fail(Throwable cause) {
            failure.compareAndSet(null, cause);
            for (Thread worker : workers) {
                if (worker != null) {
                    worker.interrupt();
                }
            }
        }

        boolean isWorker(Thread thread) {
            for (Thread worker : workers) {
                if (worker == thread) {
                    return true;
                }
            }
            return false;
        }
    }

    protected ParallelContext(int num_threads_) {
        if (num_threads_ < 1) {
            throw new IllegalArgumentException("Number of threads must be positive");
        }
        num_threads = num_threads_;
    }

    public int getNumThreads() { return num_threads; }

    // Cancel an active sweep and wait until it has stopped. Calling this from
    // its own callback requests cancellation without waiting for itself.
    public final void terminate() {
        Execution execution;
        synchronized (lifecycle) {
            terminated = true;
            execution = active;
            if (execution == null) {
                return;
            }
            execution.fail(new IllegalStateException("Parallel context was terminated"));
            if (execution.workers.length == 0 && execution.caller != Thread.currentThread()) {
                execution.caller.interrupt();
            }
        }
        if (execution.caller == Thread.currentThread()
                || execution.isWorker(Thread.currentThread())) {
            return;
        }
        boolean interrupted = false;
        while (true) {
            try {
                execution.complete.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    // Ranges in CSC contain distinct destinations. CSR and COO instead contain
    // distinct sources, so their read-modify-write callbacks need destination
    // locks. Callbacks must read immutable source state during a sweep.
    protected final Relax prepareRelax(SparseMatrix matrix, Relax relax, boolean parallel) {
        Objects.requireNonNull(matrix, "matrix");
        Objects.requireNonNull(relax, "relax");
        Object[] locks = null;
        if (parallel && !matrix.isDestinationPartitioned()) {
            locks = new Object[Math.max(1, Math.min(1024, matrix.getNumVertices()))];
            for (int i = 0; i < locks.length; ++i) {
                locks[i] = new Object();
            }
        }
        final Object[] destinationLocks = locks;
        return (source, destination) -> {
            if (terminated || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Edge traversal was cancelled");
            }
            if (destinationLocks == null) {
                relax.relax(source, destination);
            } else {
                synchronized (destinationLocks[destination % destinationLocks.length]) {
                    relax.relax(source, destination);
                }
            }
        };
    }

    // Execute a synchronous sweep. Every worker is joined before this method
    // returns, including when a callback fails or the calling thread is interrupted.
    protected final void execute(List<Runnable> actions) {
        Execution execution = new Execution(actions.size() > 1 ? actions.size() : 0);
        for (int i = 0; i < execution.workers.length; ++i) {
            final Runnable action = actions.get(i);
            execution.workers[i] = new Thread(() -> runAction(execution, action),
                    "graph-worker-" + i);
        }
        synchronized (lifecycle) {
            if (terminated) {
                throw new IllegalStateException("Parallel context was terminated");
            }
            if (active != null) {
                throw new IllegalStateException("An edge traversal is already running");
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Calling thread is interrupted");
            }
            active = execution;
            try {
                for (Thread worker : execution.workers) {
                    if (execution.failure.get() != null) {
                        break;
                    }
                    worker.start();
                }
            } catch (Throwable cause) {
                execution.fail(cause);
            }
        }
        boolean interrupted = false;
        try {
            if (actions.size() == 1) {
                runAction(execution, actions.get(0));
            }
            for (Thread worker : execution.workers) {
                while (true) {
                    try {
                        worker.join();
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                        execution.fail(new IllegalStateException("Edge traversal was interrupted", e));
                    }
                }
            }
            // A serial callback may restore its interrupt flag on the final
            // edge, and join need not block when workers have already exited.
            if (Thread.currentThread().isInterrupted()) {
                execution.fail(new CancellationException("Calling thread was interrupted"));
            }
        } finally {
            synchronized (lifecycle) {
                active = null;
                execution.complete.countDown();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        Throwable cause = execution.failure.get();
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        if (cause != null) {
            throw new IllegalStateException("Edge traversal failed", cause);
        }
    }

    private static void runAction(Execution execution, Runnable action) {
        if (execution.failure.get() != null) {
            return;
        }
        try {
            action.run();
            // There may be no following edge on which prepareRelax can notice
            // a callback's restored or explicitly requested interrupt flag.
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Edge traversal thread was interrupted");
            }
        } catch (Throwable cause) {
            execution.fail(cause);
        }
    }

    public abstract void edgemap( SparseMatrix matrix, Relax relax );
}
