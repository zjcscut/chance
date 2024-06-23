package cn.vlts.chance;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A choice determines whether next attempt should be executed.
 *
 * @author throwable
 * @since 2024/6/17 23:58
 */
@FunctionalInterface
public interface Choice<V, E extends Throwable> {

    /**
     * Determines whether next attempt should be executed.
     *
     * @param attempt the last failed attempt
     * @return whether next attempt should be executed
     */
    boolean shouldRetryNext(Attempt<V, E> attempt);

    /**
     * Create a MaxTimesLimitChoice instance, static factory method.
     *
     * @param maxTimes the max attempt times limit
     * @return a MaxTimesLimitChoice instance
     */
    static <V, E extends Throwable> Choice<V, E> newMaxTimesLimitChoice(int maxTimes) {
        return new MaxTimesLimitChoice<>(maxTimes);
    }

    /**
     * Create an ExceptionTypeMapChoice instance, static factory method.
     *
     * @param exceptionTypeMap the map of exception type, key represents exception type class, value represents
     *                         retryable or not
     * @return an ExceptionTypeMapChoice instance
     */
    static <V, E extends Throwable> Choice<V, E> newExceptionTypeMapChoice(Map<Class<? extends E>, Boolean> exceptionTypeMap) {
        return new ExceptionTypeMapChoice<>(exceptionTypeMap);
    }

    /**
     * Create a ResultPredicateListChoice instance, static factory method.
     *
     * @param resultPredicateList the result predicate collection
     * @return a ResultPredicateListChoice instance
     */
    static <V, E extends Throwable> Choice<V, E> newResultPredicateListChoice(List<Predicate<V>> resultPredicateList) {
        return new ResultPredicateListChoice<>(resultPredicateList);
    }

    /**
     * Create a CancelJudgeChoice instance, static factory method.
     *
     * @return a CancelJudgeChoice instance
     */
    static <V, E extends Throwable> Choice<V, E> newCancelJudgeChoice() {
        return new CancelJudgeChoice<>();
    }

    /**
     * Create a ForeverChoice instance, static factory method.
     *
     * @return a ForeverChoice instance
     */
    static <V, E extends Throwable> Choice<V, E> newForeverChoice() {
        return new ForeverChoice<>();
    }

    /**
     * Create a NeverChoice instance, static factory method.
     *
     * @return a NeverChoice instance
     */
    static <V, E extends Throwable> Choice<V, E> newNeverChoice() {
        return new NeverChoice<>();
    }

    /**
     * Create a MaxCallingTimeLimitChoice instance, static factory method.
     *
     * @param maxDuration the max duration limit between the initial time of the call and now
     * @return a MaxCallingTimeLimitChoice instance
     */
    static <V, E extends Throwable> Choice<V, E> newMaxCallingTimeLimitChoice(long maxDuration) {
        return new MaxCallingTimeLimitChoice<>(maxDuration);
    }

    /**
     * Create a CompositeChoice instance, static factory method.
     *
     * @param choices one or more choice instances
     * @return a CompositeChoice instance
     */
    @SafeVarargs
    static <V, E extends Throwable> Choice<V, E> newCompositeChoice(Choice<V, E>... choices) {
        return new CompositeChoice<>(Arrays.asList(choices));
    }

    /**
     * Returns a choice implement which limits the maximum times of attempts.
     */
    class MaxTimesLimitChoice<V, E extends Throwable> implements Choice<V, E> {

        private final int maxTimes;

        private MaxTimesLimitChoice(int maxTimes) {
            if (maxTimes <= 0) {
                throw new IllegalArgumentException("MaxTimes must be greater than 0.");
            }
            this.maxTimes = maxTimes;
        }

        @Override
        public boolean shouldRetryNext(Attempt<V, E> attempt) {
            return this.maxTimes > attempt.attemptTimes();
        }
    }

    /**
     * Returns a choice implement which contains one or more choices.
     */
    class CompositeChoice<V, E extends Throwable> implements Choice<V, E> {

        private final List<Choice<V, E>> choiceList;

        private CompositeChoice(List<Choice<V, E>> choiceList) {
            if (Objects.isNull(choiceList) || choiceList.isEmpty()) {
                throw new IllegalArgumentException("ChoiceList must not be empty.");
            }
            this.choiceList = new ArrayList<>();
            this.choiceList.addAll(choiceList);
        }

        @Override
        public boolean shouldRetryNext(Attempt<V, E> attempt) {
            return this.choiceList.stream().allMatch(choice -> choice.shouldRetryNext(attempt));
        }
    }

    class ExceptionTypeMapChoice<V, E extends Throwable> implements Choice<V, E> {

        private final ConcurrentMap<Class<? extends E>, Boolean> exceptionTypeMap = new ConcurrentHashMap<>();

        private ExceptionTypeMapChoice(Map<Class<? extends E>, Boolean> exceptionTypeMap) {
            if (Objects.nonNull(exceptionTypeMap)) {
                this.exceptionTypeMap.putAll(exceptionTypeMap);
            }
        }

        @Override
        public boolean shouldRetryNext(Attempt<V, E> attempt) {
            if (!attempt.hasException()) {
                return true;
            }
            E cause = attempt.rootCauseNow();
            Class<?> exceptionType = cause.getClass();
            for (Map.Entry<Class<? extends E>, Boolean> entry : this.exceptionTypeMap.entrySet()) {
                if (Objects.equals(entry.getKey(), exceptionType)) {
                    return entry.getValue();
                }
            }
            return true;
        }
    }

    class ResultPredicateListChoice<V, E extends Throwable> implements Choice<V, E> {

        private final List<Predicate<V>> resultPredicateList = new ArrayList<>();

        private ResultPredicateListChoice(List<Predicate<V>> resultPredicateList) {
            if (Objects.nonNull(resultPredicateList)) {
                this.resultPredicateList.addAll(resultPredicateList);
            }
        }

        @Override
        public boolean shouldRetryNext(Attempt<V, E> attempt) {
            if (!attempt.hasResult()) {
                return true;
            }
            V result = attempt.resultNow();
            return this.resultPredicateList.stream()
                    .anyMatch(resultPredicate -> resultPredicate.test(result));
        }
    }

    class CancelJudgeChoice<V, E extends Throwable> implements Choice<V, E> {

        private CancelJudgeChoice() {
        }

        @Override
        public boolean shouldRetryNext(Attempt<V, E> attempt) {
            return !attempt.isCancelled();
        }
    }

    class ForeverChoice<V, E extends Throwable> implements Choice<V, E> {

        private ForeverChoice() {
        }

        @Override
        public boolean shouldRetryNext(Attempt<V, E> attempt) {
            return true;
        }
    }

    class NeverChoice<V, E extends Throwable> implements Choice<V, E> {

        private NeverChoice() {
        }

        @Override
        public boolean shouldRetryNext(Attempt<V, E> attempt) {
            return false;
        }
    }

    class MaxCallingTimeLimitChoice<V, E extends Throwable> implements Choice<V, E> {

        private final long maxDuration;

        private MaxCallingTimeLimitChoice(long maxDuration) {
            if (maxDuration <= 0) {
                throw new IllegalArgumentException("MaxDuration must be greater than 0.");
            }
            this.maxDuration = maxDuration;
        }

        @Override
        public boolean shouldRetryNext(Attempt<V, E> attempt) {
            return this.maxDuration >= TimeUnit.NANOSECONDS.toMillis(attempt.completionNanos() - attempt.initialNanos());
        }
    }
}
