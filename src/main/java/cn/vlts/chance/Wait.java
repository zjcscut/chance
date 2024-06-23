package cn.vlts.chance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A wait is used to compute the waiting duration for next attempt(if necessary).
 *
 * @author throwable
 * @since 2024/6/17 11:57
 */
@FunctionalInterface
public interface Wait<V, E extends Throwable> {

    /**
     * Compute the waiting duration for next attempt. Wait instance will not block current call, waiting duration will
     * be passed to the {@link Blocker}.
     *
     * @param attempt current attempt
     * @return the waiting duration
     */
    long computeWaitDuration(Attempt<V, E> attempt);

    /**
     * Create a NoWait instance, static factory method.
     *
     * @return a NoWait instance
     */
    static <V, E extends Throwable> Wait<V, E> newNoWait() {
        return new NoWait<>();
    }

    /**
     * Create a FixedWait instance, static factory method.
     *
     * @param duration fixed wait duration in millis
     * @return a FixedWait instance
     */
    static <V, E extends Throwable> Wait<V, E> newFixedWait(long duration) {
        return new FixedWait<>(duration);
    }

    /**
     * Create a RandomWait instance, static factory method.
     *
     * @param minDuration minimum random duration in millis (inclusive)
     * @param maxDuration maximum random duration in millis (exclusive)
     * @return a RandomWait instance
     */
    static <V, E extends Throwable> Wait<V, E> newRandomWait(long minDuration, long maxDuration) {
        return new RandomWait<>(minDuration, maxDuration);
    }

    /**
     * Create an IncrementingWait instance, static factory method.
     *
     * @param initialDuration   initial duration in millis
     * @param increasedDuration increased duration in millis
     * @return an IncrementingWait instance
     */
    static <V, E extends Throwable> Wait<V, E> newIncrementingWait(long initialDuration, long increasedDuration) {
        return new IncrementingWait<>(initialDuration, increasedDuration);
    }

    /**
     * Create an ExponentialBackOffWait instance, static factory method.
     *
     * @param multiplier  as the multiplier to calculate the wait time
     * @param maxDuration maximum wait duration in millis
     * @return an ExponentialBackOffWait instance
     */
    static <V, E extends Throwable> Wait<V, E> newExponentialBackOffWait(long multiplier, long maxDuration) {
        return new ExponentialBackOffWait<>(multiplier, maxDuration);
    }

    /**
     * Create an ExponentialBackOffWait instance, static factory method.
     *
     * @param base        as the base  to calculate the wait time
     * @param multiplier  as the multiplier  to calculate the wait time
     * @param maxDuration maximum wait duration in millis
     * @return an ExponentialBackOffWait instance
     */
    static <V, E extends Throwable> Wait<V, E> newExponentialBackOffWait(double base, long multiplier, long maxDuration) {
        return new ExponentialBackOffWait<>(base, multiplier, maxDuration);
    }

    /**
     * Create a FibonacciBackoffWait instance, static factory method.
     *
     * @param multiplier  as a multiplier to calculate the wait time
     * @param maxDuration maximum wait duration in millis
     * @return a FibonacciBackoffWait instance
     */
    static <V, E extends Throwable> Wait<V, E> newFibonacciBackoffWait(long multiplier, long maxDuration) {
        return new FibonacciBackoffWait<>(multiplier, maxDuration);
    }

    /**
     * Create a CompositeWait instance, static factory method.
     *
     * @param waits one or more wait instances
     * @return a CompositeWait instance
     */
    @SafeVarargs
    static <V, E extends Throwable> Wait<V, E> newCompositeWait(Wait<V, E>... waits) {
        return new CompositeWait<>(Arrays.asList(waits));
    }

    class NoWait<V, E extends Throwable> implements Wait<V, E> {

        private NoWait() {
        }

        @Override
        public long computeWaitDuration(Attempt<V, E> attempt) {
            return 0L;
        }
    }

    /**
     * Returns a wait implement which computes a fixed wait duration before next attempt. The unit of duration
     * is milliseconds.
     */
    class FixedWait<V, E extends Throwable> implements Wait<V, E> {

        private final long duration;

        private FixedWait(long duration) {
            if (duration <= 0) {
                throw new IllegalArgumentException("Duration must be greater than 0.");
            }
            this.duration = duration;
        }

        @Override
        public long computeWaitDuration(Attempt<V, E> attempt) {
            return this.duration;
        }
    }

    /**
     * Returns a wait implement which computes a random wait duration before next attempt. The unit of minDuration
     * or maxDuration is milliseconds. The random duration is a random chosen value between the minDuration (inclusive)
     * and the maxDuration (exclusive).
     */
    class RandomWait<V, E extends Throwable> implements Wait<V, E> {

        private final long minDuration;

        private final long maxDuration;

        private RandomWait(long minDuration, long maxDuration) {
            if (minDuration <= 0) {
                throw new IllegalArgumentException("MinDuration must be greater than 0.");
            }
            if (maxDuration < minDuration) {
                throw new IllegalArgumentException("MaxDuration must be greater than minDuration.");
            }
            this.minDuration = minDuration;
            this.maxDuration = maxDuration;
        }

        @Override
        public long computeWaitDuration(Attempt<V, E> attempt) {
            return ThreadLocalRandom.current().nextLong(this.minDuration, this.maxDuration);
        }
    }

    /**
     * Returns a wait implement that contains one or more waits. The total wait duration will be the sum of all the waits.
     */
    class CompositeWait<V, E extends Throwable> implements Wait<V, E> {

        private final List<Wait<V, E>> waitList;

        private CompositeWait(List<Wait<V, E>> waitList) {
            if (Objects.isNull(waitList) || waitList.isEmpty()) {
                throw new IllegalArgumentException("WaitList must not be empty.");
            }
            this.waitList = new ArrayList<>();
            this.waitList.addAll(waitList);
        }

        @Override
        public long computeWaitDuration(Attempt<V, E> attempt) {
            return this.waitList.stream().map(wait -> wait.computeWaitDuration(attempt)).reduce(0L, Long::sum);
        }
    }

    /**
     * Returns a wait implement which the wait duration will be increased for the next attempt. The unit of
     * initialDuration or increasedDuration is milliseconds. The wait duration for the next attempt is computed by:
     * initialDuration + (current_attempt.attemptTimes() - 1) * this.increasedDuration.
     */
    class IncrementingWait<V, E extends Throwable> implements Wait<V, E> {

        private final long initialDuration;

        private final long increasedDuration;

        private IncrementingWait(long initialDuration, long increasedDuration) {
            if (initialDuration < 0) {
                throw new IllegalArgumentException("InitialDuration must be greater than 0 or equal to 0.");
            }
            if (increasedDuration <= 0) {
                throw new IllegalArgumentException("IncreasedDuration must be greater than 0.");
            }
            this.initialDuration = initialDuration;
            this.increasedDuration = increasedDuration;
        }

        @Override
        public long computeWaitDuration(Attempt<V, E> attempt) {
            return this.initialDuration + (attempt.attemptTimes() - 1) * this.increasedDuration;
        }
    }

    /**
     * Returns a wait implement which the wait duration will be computed by exponential backoff algorithm. The unit of
     * multiplier or maxDuration is milliseconds. The default value of base is 2. The wait duration for the next
     * attempt is computed by:
     * min(maxDuration , round[base ^ current_attempt.attemptTimes() * multiplier]).
     */
    class ExponentialBackOffWait<V, E extends Throwable> implements Wait<V, E> {

        private static final double DEFAULT_BASE = 2;

        private final double base;

        private final long multiplier;

        private final long maxDuration;

        private ExponentialBackOffWait(long multiplier, long maxDuration) {
            this(DEFAULT_BASE, multiplier, maxDuration);
        }

        private ExponentialBackOffWait(double base, long multiplier, long maxDuration) {
            if (base <= 0) {
                throw new IllegalArgumentException("Base must be greater than 0.");
            }
            if (multiplier <= 0) {
                throw new IllegalArgumentException("Multiplier must be greater than 0.");
            }
            if (maxDuration <= 0) {
                throw new IllegalArgumentException("MaxDuration must be greater than 0.");
            }
            if (maxDuration <= multiplier) {
                throw new IllegalArgumentException("MaxDuration must be greater than multiplier.");
            }
            this.base = base;
            this.maxDuration = maxDuration;
            this.multiplier = multiplier;
        }

        @Override
        public long computeWaitDuration(Attempt<V, E> attempt) {
            double factor = Math.pow(this.base, attempt.attemptTimes());
            long duration = Math.round(factor * this.multiplier);
            if (duration > this.maxDuration) {
                duration = this.maxDuration;
            }
            return duration;
        }
    }

    /**
     * Returns a wait implement which the wait duration will be computed by fibonacci backoff algorithm. The unit of
     * multiplier or maxDuration is milliseconds. The wait duration for the next attempt is computed by:
     * min(maxDuration , round[fibonacci(current_attempt.attemptTimes()) * multiplier]).
     * The method FibonacciBackoffWait.fib(int n) provides the implement of fibonacci algorithm as bellow.
     */
    class FibonacciBackoffWait<V, E extends Throwable> implements Wait<V, E> {

        private final double multiplier;

        private final long maxDuration;

        private FibonacciBackoffWait(long multiplier, long maxDuration) {
            if (multiplier <= 0) {
                throw new IllegalArgumentException("Multiplier must be greater than 0.");
            }
            if (maxDuration <= 0) {
                throw new IllegalArgumentException("MaxDuration must be greater than 0.");
            }
            if (maxDuration <= multiplier) {
                throw new IllegalArgumentException("MaxDuration must be greater than multiplier.");
            }
            this.maxDuration = maxDuration;
            this.multiplier = multiplier;
        }

        @Override
        public long computeWaitDuration(Attempt<V, E> attempt) {
            int factor = fib(attempt.attemptTimes());
            long duration = Math.round(factor * this.multiplier);
            if (duration > this.maxDuration) {
                duration = this.maxDuration;
            }
            return duration;
        }

        private int fib(int n) {
            if (1 == n) {
                return 0;
            }
            if (2 == n) {
                return 1;
            }
            int anc = 0;
            int prev = 1;
            int r = 0;
            for (int i = 0; i < n - 2; i++) {
                r = prev + anc;
                anc = prev;
                prev = r;
            }
            return r;
        }
    }
}
