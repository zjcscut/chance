package cn.vlts.chance;

import java.util.Objects;
import java.util.concurrent.*;

/**
 * The time limiter, a wrapper to wrap any single attempt in a time limit, where it will possibly be interrupted if
 * the time limit is exceeded or completed exceptionally if the single attempt is failed.
 *
 * @author throwable
 * @since 2024/6/18 00:02
 */
@FunctionalInterface
public interface TimeLimiter<V> {

    /**
     * Invokes a specified {@link Callable}, timing out after the specified time limit.
     *
     * @param callable the callable to the time limiter
     * @param duration the duration to the time limiter
     * @param unit     the duration unit to the time limiter
     * @return the return value of the given supplier
     * @throws TimeoutException     if the wait timed out
     * @throws InterruptedException if the current thread was interrupted while waiting
     * @throws ExecutionException   if this future completed exceptionally
     */
    V callWithTimeout(Callable<V> callable, long duration, TimeUnit unit) throws TimeoutException,
            InterruptedException, ExecutionException;

    /**
     * Create a NoTimeLimiter instance, static factory method.
     *
     * @return a NoTimeLimiter instance
     */
    static <V> TimeLimiter<V> newNoTimeLimiter() {
        return new NoTimeLimiter<>();
    }

    /**
     * Create a CompletableFutureTimeLimiter instance, static factory method.
     *
     * @return a CompletableFutureTimeLimiter instance
     */
    static <V> TimeLimiter<V> newCompletableFutureTimeLimiter() {
        return new CompletableFutureTimeLimiter<>();
    }

    /**
     * Create a CompletableFutureTimeLimiter instance, static factory method.
     *
     * @param executor the executor to use for asynchronous execution
     * @return a CompletableFutureTimeLimiter instance
     */
    static <V> TimeLimiter<V> newCompletableFutureTimeLimiter(Executor executor) {
        return new CompletableFutureTimeLimiter<>(executor);
    }

    /**
     * Create an ExecutorServiceTimeLimiter instance, static factory method.
     *
     * @param executorService the executor service which async task to submit
     * @return an ExecutorServiceTimeLimiter instance
     */
    static <V> TimeLimiter<V> newExecutorServiceTimeLimiter(ExecutorService executorService) {
        return new ExecutorServiceTimeLimiter<>(executorService);
    }

    /**
     * No time limiter, invokes the specified {@link Callable} directly.
     */
    class NoTimeLimiter<V> implements TimeLimiter<V> {

        private NoTimeLimiter() {
        }

        @Override
        public V callWithTimeout(Callable<V> callable, long duration, TimeUnit unit) throws TimeoutException,
                InterruptedException, ExecutionException {
            try {
                return callable.call();
            } catch (Error | RuntimeException e) {
                throw e;
            } catch (Throwable t) {
                throw new ExecutionException(t);
            }
        }
    }

    /**
     * Time limiter which invokes the specified {@link Callable} wrapped by {@link CompletableFuture}. If the
     * {@link Executor} parameter is null, ASYNC_POOL(commonPool) will be used.
     */
    class CompletableFutureTimeLimiter<V> implements TimeLimiter<V> {

        private final Executor executor;

        private CompletableFutureTimeLimiter() {
            this(null);
        }

        private CompletableFutureTimeLimiter(Executor executor) {
            this.executor = executor;
        }

        @Override
        public V callWithTimeout(Callable<V> callable, long duration, TimeUnit unit)
                throws TimeoutException, InterruptedException, ExecutionException {
            CompletableFuture<V> completableFuture = adapt(callable, this.executor);
            return completableFuture.get(duration, unit);
        }

        private static <T> CompletableFuture<T> adapt(Callable<T> callable, Executor executor) {
            if (Objects.nonNull(executor)) {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return callable.call();
                    } catch (Error | RuntimeException e) {
                        throw e;
                    } catch (Throwable t) {
                        throw new CompletionException(t);
                    }
                }, executor);
            }
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return callable.call();
                } catch (Error | RuntimeException e) {
                    throw e;
                } catch (Throwable t) {
                    throw new CompletionException(t);
                }
            });
        }
    }

    /**
     * Time limiter which submits the specified {@link Callable} as futureTask to an {@link ExecutorService} instance.
     */
    class ExecutorServiceTimeLimiter<V> implements TimeLimiter<V> {

        private final ExecutorService executorService;

        private ExecutorServiceTimeLimiter(ExecutorService executorService) {
            this.executorService = executorService;
        }

        @Override
        public V callWithTimeout(Callable<V> callable, long duration, TimeUnit unit)
                throws TimeoutException, InterruptedException, ExecutionException {
            return this.executorService.submit(callable).get(duration, unit);
        }
    }
}
