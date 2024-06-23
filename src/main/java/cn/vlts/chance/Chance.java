package cn.vlts.chance;

import net.sf.cglib.proxy.MethodInterceptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * A chance, just likes its name, a chance to execute a call. A call will be wrapped as {@link Callable}, then executes
 * it with a specified time limiter, retries this step until it succeeds or stops by the {@link Choice}.
 * <p></p>
 * Every single retry context, a new {@link Attempt} will be created to record the state, context metadata of
 * current calling attempt. When a call stops by the {@link Choice}, the recoverPredicate decides whether it
 * can be recovered or not.
 * <p></p>
 * If the call can be recovered, then the {@link Recovery} will be called, which provides the final succeed result
 * of the call.
 * <p></p>
 * During each attempt, the {@link Wait} and the {@link Blocker} will compute the blocking time and block util next
 * attempt.
 * <p></p>
 * If a {@link Listener} has been added to the chance, it will be fired when the state of chance changed.
 *
 * @author throwable
 * @since 2024/6/18 19:06
 */
@FunctionalInterface
public interface Chance<V> {

    /**
     * A call will be wrapped as {@link Callable}, then executes it with a specified time limiter,
     * retries this step until it succeeds or stops by the {@link Choice}. During each attempt, the {@link Wait}
     * and the {@link Blocker} will compute the blocking time and block util next attempt.
     *
     * @param callable the callable to execute
     * @return the result returned by the callable
     * @throws ChanceException if {@code callable} throws a {@code Exception} or the maximum retires was exceeded
     */
    V call(Callable<V> callable) throws ChanceException;

    /**
     * Wrap a callable to execute with chance.
     *
     * @param callable the callable to execute
     * @return a new callable which was wrapped by chance
     */
    default Callable<V> wrap(Callable<V> callable) {
        return () -> Chance.this.call(callable);
    }

    /**
     * Wrap a runnable with result to execute with chance.
     *
     * @param runnable the runnable to execute
     * @param result   the result of the runnable
     * @return a new runnable which was wrapped by chance
     */
    default Callable<V> wrap(Runnable runnable, V result) {
        return () -> Chance.this.call(Executors.callable(runnable, result));
    }

    /**
     * Create a chance builder instance.
     *
     * @return a chance builder
     */
    static <V, E extends Throwable> Builder<V, E> newBuilder() {
        return new Builder<>();
    }

    /**
     * Returns an instance of {@code type} that delegates all method calls to the {@code target} object, enforcing
     * the specified {@link Chance} on each call.
     *
     * @param type          the type you wish the returned proxy to implement
     * @param target        the object to proxy
     * @param proxyStrategy the proxy strategy
     * @return a proxy which wraps the retryable call with chance
     */
    static <T> T newProxy(Class<? super T> type, T target, ProxyStrategy proxyStrategy) {
        return ChanceProxyLoader.DEFAULT.createProxy(type, target, proxyStrategy);
    }

    /**
     * Returns an instance of {@code type} that delegates all method calls to the {@code target} object, enforcing
     * the specified {@link Chance} on each call. Use {@link ProxyStrategy#AUTO} proxy strategy.
     *
     * @param type   the type you wish the returned proxy to implement
     * @param target the object to proxy
     * @return a proxy which wraps the retryable call with chance
     */
    static <T> T newProxy(Class<? super T> type, T target) {
        return ChanceProxyLoader.DEFAULT.createProxy(type, target, ProxyStrategy.AUTO);
    }

    /**
     * Proxy strategy.
     */
    enum ProxyStrategy {

        /**
         * Auto guess the best proxy strategy.
         */
        AUTO,

        /**
         * JDK proxy strategy.
         */
        JDK,

        /**
         * CGLIB proxy strategy.
         */
        CGLIB
    }

    /**
     * The chance builder.
     */
    class Builder<V, E extends Throwable> {

        private long timeLimitDuration = 0L;

        private TimeUnit timeLimitUnit = TimeUnit.MILLISECONDS;

        private TimeLimiter<V> timeLimiter = TimeLimiter.newNoTimeLimiter();

        private Blocker<V, E> blocker = Blocker.newThreadSleepBlocker();

        private final List<Listener<V, E>> listeners = new ArrayList<>();

        private final List<Wait<V, E>> waits = new ArrayList<>();

        private final List<Choice<V, E>> choices = new ArrayList<>();

        private BiPredicate<Recovery<V>, Attempt<V, ?>> recoverPredicate = (r, a) -> false;

        private Predicate<Attempt<V, E>> attemptPredicate = Attempt::hasResult;

        private Recovery<V> recovery;

        public Builder<V, E> withListener(Listener<V, E> listener) {
            if (Objects.isNull(listener)) {
                throw new IllegalArgumentException("Listener must not be null.");
            }
            this.listeners.add(listener);
            return this;
        }

        public Builder<V, E> withTimeLimiter(TimeLimiter<V> timeLimiter, long timeLimitDuration, TimeUnit timeLimitUnit) {
            if (Objects.isNull(timeLimiter)) {
                throw new IllegalArgumentException("TimeLimiter must not be null.");
            }
            if (timeLimitDuration <= 0) {
                throw new IllegalArgumentException("TimeLimitDuration must be greater than 0.");
            }
            if (Objects.isNull(timeLimitUnit)) {
                throw new IllegalArgumentException("TimeLimitUnit must not be null.");
            }
            this.timeLimiter = timeLimiter;
            this.timeLimitDuration = timeLimitDuration;
            this.timeLimitUnit = timeLimitUnit;
            return this;
        }

        public Builder<V, E> withBlocker(Blocker<V, E> blocker) {
            if (Objects.isNull(blocker)) {
                throw new IllegalArgumentException("Blocker must not be null.");
            }
            this.blocker = blocker;
            return this;
        }

        public Builder<V, E> withChoice(Choice<V, E> choice) {
            if (Objects.isNull(choice)) {
                throw new IllegalArgumentException("Choice must not be null.");
            }
            this.choices.add(choice);
            return this;
        }

        public Builder<V, E> withWait(Wait<V, E> wait) {
            if (Objects.isNull(wait)) {
                throw new IllegalArgumentException("Wait must not be null.");
            }
            this.waits.add(wait);
            return this;
        }

        public Builder<V, E> withFixedWaitTime(long duration, TimeUnit unit) {
            long waitDuration = unit.toMillis(duration);
            this.waits.add(Wait.newFixedWait(waitDuration));
            return this;
        }

        public Builder<V, E> withRandomWaitTime(long minDuration, long maxDuration, TimeUnit unit) {
            long minWaitDuration = unit.toMillis(minDuration);
            long maxWaitDuration = unit.toMillis(maxDuration);
            this.waits.add(Wait.newRandomWait(minWaitDuration, maxWaitDuration));
            return this;
        }

        public Builder<V, E> withIncrementingWaitTime(long initialDuration, long increasedDuration, TimeUnit unit) {
            long initialWaitDuration = unit.toMillis(initialDuration);
            long increasedWaitDuration = unit.toMillis(increasedDuration);
            this.waits.add(Wait.newIncrementingWait(initialWaitDuration, increasedWaitDuration));
            return this;
        }

        public Builder<V, E> withExponentialWaitTime(long multiplier, long maxDuration, TimeUnit unit) {
            long waitMultiplier = unit.toMillis(multiplier);
            long maxWaitDuration = unit.toMillis(maxDuration);
            this.waits.add(Wait.newExponentialBackOffWait(waitMultiplier, maxWaitDuration));
            return this;
        }

        public Builder<V, E> withExponentialWaitTime(double base, long multiplier, long maxDuration, TimeUnit unit) {
            long waitMultiplier = unit.toMillis(multiplier);
            long maxWaitDuration = unit.toMillis(maxDuration);
            this.waits.add(Wait.newExponentialBackOffWait(base, waitMultiplier, maxWaitDuration));
            return this;
        }

        public Builder<V, E> withFibonacciWaitTime(long multiplier, long maxDuration, TimeUnit unit) {
            long waitMultiplier = unit.toMillis(multiplier);
            long maxWaitDuration = unit.toMillis(maxDuration);
            this.waits.add(Wait.newFibonacciBackoffWait(waitMultiplier, maxWaitDuration));
            return this;
        }

        public Builder<V, E> withMaxRetryTimes(int maxRetryTimes) {
            this.choices.add(Choice.newMaxTimesLimitChoice(maxRetryTimes));
            return this;
        }

        public Builder<V, E> withRetryForever() {
            this.choices.add(Choice.newForeverChoice());
            return this;
        }

        public Builder<V, E> withNeverRetry() {
            this.choices.add(Choice.newNeverChoice());
            return this;
        }

        public Builder<V, E> withMaxCallingDuration(long maxDuration, TimeUnit unit) {
            long maxCallingDuration = unit.toMillis(maxDuration);
            this.choices.add(Choice.newMaxCallingTimeLimitChoice(maxCallingDuration));
            return this;
        }

        public Builder<V, E> withRetryableExceptions(Map<Class<? extends E>, Boolean> retryableExceptions) {
            this.choices.add(Choice.newExceptionTypeMapChoice(retryableExceptions));
            return this;
        }

        public Builder<V, E> withRetryableResult(Predicate<V> retryableResult) {
            if (Objects.nonNull(retryableResult)) {
                return withRetryableResults(Collections.singletonList(retryableResult));
            }
            return this;
        }

        public Builder<V, E> withRetryableResults(List<Predicate<V>> retryableResults) {
            if (Objects.nonNull(retryableResults)) {
                this.choices.add(Choice.newResultPredicateListChoice(retryableResults));
                retryableResults.stream().map(Predicate::negate).forEach(resultPredicate -> this.attemptPredicate =
                        this.attemptPredicate.and(attempt -> resultPredicate.test(attempt.resultNow())));
            }
            return this;
        }

        public Builder<V, E> withRecoverPredicate(BiPredicate<Recovery<V>, Attempt<V, ?>> recoverPredicate) {
            this.recoverPredicate = this.recoverPredicate.or(recoverPredicate);
            return this;
        }

        public Builder<V, E> withRecoverIfHasException() {
            this.recoverPredicate = this.recoverPredicate.or((r, a) -> a.hasException());
            return this;
        }

        public Builder<V, E> withAttemptPredicate(Predicate<Attempt<V, E>> attemptPredicate) {
            this.attemptPredicate = this.attemptPredicate.and(attemptPredicate);
            return this;
        }

        public Builder<V, E> withRecovery(Recovery<V> recovery) {
            this.recovery = recovery;
            return this;
        }

        @SuppressWarnings("unchecked")
        public Chance<V> build() {
            Wait<V, E> waitToUse;
            if (this.waits.isEmpty()) {
                waitToUse = Wait.newNoWait();
            } else if (1 == this.waits.size()) {
                waitToUse = this.waits.get(0);
            } else {
                waitToUse = Wait.newCompositeWait(this.waits.toArray(new Wait[0]));
            }
            List<Choice<V, E>> choiceListToUse = new ArrayList<>();
            choiceListToUse.add(Choice.newCancelJudgeChoice());
            if (!this.choices.isEmpty()) {
                choiceListToUse.addAll(this.choices);
            }
            Choice<V, E> choiceToUse = Choice.newCompositeChoice(choiceListToUse.toArray(new Choice[0]));
            return new DefaultChance<>(waitToUse, choiceToUse, this.timeLimiter, this.blocker, this.recoverPredicate,
                    this.attemptPredicate, this.listeners, this.timeLimitDuration, this.timeLimitUnit, this.recovery);
        }
    }

    class DefaultChance<V, E extends Throwable> implements Chance<V> {

        private final Wait<V, E> wait;

        private final Choice<V, E> choice;

        private final TimeLimiter<V> timeLimiter;

        private final Blocker<V, E> blocker;

        private final BiPredicate<Recovery<V>, Attempt<V, ?>> recoverPredicate;

        private final Predicate<Attempt<V, E>> attemptPredicate;

        private final List<Listener<V, E>> listeners = new ArrayList<>();

        private final long timeLimitDuration;

        private final TimeUnit timeLimitUnit;

        private final Recovery<V> recovery;

        private DefaultChance(Wait<V, E> wait,
                              Choice<V, E> choice,
                              TimeLimiter<V> timeLimiter,
                              Blocker<V, E> blocker,
                              BiPredicate<Recovery<V>, Attempt<V, ?>> recoverPredicate,
                              Predicate<Attempt<V, E>> attemptPredicate,
                              List<Listener<V, E>> listeners,
                              long timeLimitDuration,
                              TimeUnit timeLimitUnit,
                              Recovery<V> recovery) {
            this.wait = wait;
            this.choice = choice;
            this.timeLimiter = timeLimiter;
            this.blocker = blocker;
            this.recoverPredicate = recoverPredicate;
            this.attemptPredicate = attemptPredicate;
            if (Objects.nonNull(listeners) && !listeners.isEmpty()) {
                this.listeners.addAll(listeners);
            }
            this.timeLimitDuration = timeLimitDuration;
            this.timeLimitUnit = timeLimitUnit;
            this.recovery = recovery;
        }

        public void fireListeners(Attempt<V, E> attempt) {
            this.listeners.forEach(listener -> listener.onFire(attempt));
        }

        @Override
        public V call(Callable<V> callable) throws ChanceException {
            long initialNanos = System.nanoTime();
            for (int attemptTimes = 1; ; attemptTimes++) {
                DefaultAttempt<V, E> attempt = new DefaultAttempt<>(initialNanos, attemptTimes);
                fireListeners(attempt);
                cancelCallIfNecessary(attempt);
                try {
                    V value = this.timeLimiter.callWithTimeout(callable, this.timeLimitDuration, this.timeLimitUnit);
                    attempt.setResult(value);
                } catch (Throwable e) {
                    attempt.setException(e);
                } finally {
                    attempt.setCompletionNanos(System.nanoTime());
                    fireListeners(attempt);
                }
                if (this.attemptPredicate.test(attempt)) {
                    return attempt.resultNow();
                }
                if (!this.choice.shouldRetryNext(attempt)) {
                    return Optional.ofNullable(this.recovery)
                            .filter(recoveryToUse -> this.recoverPredicate.test(recoveryToUse, attempt))
                            .map(recoveryToUse -> recoveryToUse.recover(attempt))
                            .orElseThrow(() -> new ChanceException(attempt.toReadOnlyAttempt(), false));
                }
                long waitDuration = this.wait.computeWaitDuration(attempt);
                try {
                    this.blocker.block(waitDuration, attempt);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ChanceException(attempt.toReadOnlyAttempt(), true);
                }
            }
        }

        private void cancelCallIfNecessary(Attempt<?, ?> attempt) throws ChanceException {
            if (attempt.isCancelled()) {
                throw new ChanceException(attempt, false);
            }
        }

        static class DefaultAttempt<V, E extends Throwable> implements Attempt<V, E> {

            private final long initialNanos;

            private final long startNanos;

            private final int attemptTimes;

            private final AtomicReference<State> stateRef = new AtomicReference<>(State.RUNNING);

            private long completionNanos;

            private Object resultHolder;

            private DefaultAttempt(long initialNanos, int attemptTimes) {
                this.initialNanos = initialNanos;
                this.startNanos = System.nanoTime();
                this.attemptTimes = attemptTimes;
            }

            @Override
            public State state() {
                return this.stateRef.get();
            }

            @Override
            public boolean cancel() {
                return this.stateRef.compareAndSet(State.RUNNING, State.CANCELLED);
            }

            @Override
            public void forceCancel() {
                this.stateRef.set(State.CANCELLED);
            }

            @SuppressWarnings("unchecked")
            @Override
            public V resultNow() {
                if (!hasResult()) {
                    throw new IllegalStateException("Invalid state, current state is not 'success'.");
                }
                return (V) this.resultHolder;
            }

            @SuppressWarnings("unchecked")
            @Override
            public E exceptionNow() {
                if (!hasException()) {
                    throw new IllegalStateException("Invalid state, current state is not 'failed'.");
                }
                return (E) this.resultHolder;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <S extends E> S rootCauseNow() {
                return (S) Utils.U.getRootCause(exceptionNow());
            }

            @Override
            public int attemptTimes() {
                return this.attemptTimes;
            }

            @Override
            public long initialNanos() {
                return this.initialNanos;
            }

            @Override
            public long startNanos() {
                return this.startNanos;
            }

            @Override
            public long completionNanos() {
                return this.completionNanos;
            }

            public void setResult(V value) {
                if (this.stateRef.compareAndSet(State.RUNNING, State.SUCCESS)) {
                    this.resultHolder = value;
                } else {
                    throw new IllegalStateException("Fail to update success state and result value.");
                }
            }

            public void setException(Throwable throwable) {
                if (this.stateRef.compareAndSet(State.RUNNING, State.FAILED)) {
                    this.resultHolder = throwable;
                } else {
                    throw new IllegalStateException("Fail to update failed state and result value.");
                }
            }

            public void setCompletionNanos(long completionNanos) {
                this.completionNanos = completionNanos;
            }

            public ReadOnlyAttempt<V, E> toReadOnlyAttempt() {
                return ReadOnlyAttempt.copyFrom(this);
            }
        }

        static class ReadOnlyAttempt<V, E extends Throwable> implements Attempt<V, E> {

            private final long initialNanos;

            private final long startNanos;

            private final int attemptTimes;

            private final State state;

            private final long completionNanos;

            private final Object resultHolder;

            private ReadOnlyAttempt(long initialNanos, long startNanos, int attemptTimes, State state,
                                    long completionNanos, Object resultHolder) {
                this.initialNanos = initialNanos;
                this.startNanos = startNanos;
                this.attemptTimes = attemptTimes;
                this.state = state;
                this.completionNanos = completionNanos;
                this.resultHolder = resultHolder;
            }

            @Override
            public boolean cancel() {
                throw new UnsupportedOperationException("Cancel is not supported.");
            }

            @Override
            public void forceCancel() {
                throw new UnsupportedOperationException("ForceCancel is not supported.");
            }

            @SuppressWarnings("unchecked")
            @Override
            public V resultNow() {
                if (!hasResult()) {
                    throw new IllegalStateException("Invalid state, current state is not 'success'.");
                }
                return (V) this.resultHolder;
            }

            @SuppressWarnings("unchecked")
            @Override
            public E exceptionNow() {
                if (!hasException()) {
                    throw new IllegalStateException("Invalid state, current state is not 'failed'.");
                }
                return (E) this.resultHolder;
            }

            @SuppressWarnings("unchecked")
            @Override
            public <S extends E> S rootCauseNow() {
                return (S) Utils.U.getRootCause(exceptionNow());
            }

            @Override
            public State state() {
                return this.state;
            }

            @Override
            public int attemptTimes() {
                return this.attemptTimes;
            }

            @Override
            public long initialNanos() {
                return this.initialNanos;
            }

            @Override
            public long startNanos() {
                return this.startNanos;
            }

            @Override
            public long completionNanos() {
                return this.completionNanos;
            }

            private static <V, E extends Throwable> ReadOnlyAttempt<V, E> copyFrom(DefaultAttempt<V, E> defaultAttempt) {
                return new ReadOnlyAttempt<>(
                        defaultAttempt.initialNanos,
                        defaultAttempt.startNanos,
                        defaultAttempt.attemptTimes,
                        defaultAttempt.state(),
                        defaultAttempt.completionNanos,
                        defaultAttempt.resultHolder);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    class ChanceProxyLoader {

        private static boolean ENABLE_CGLIB;

        private static final ChanceProxyLoader DEFAULT = new ChanceProxyLoader();

        private final ConcurrentMap<Method, Chance> serviceMethodCache = new ConcurrentHashMap<>();

        private <T> T createProxy(Class<? super T> type, T tatget, ProxyStrategy proxyStrategy) {
            boolean isInterfaceType = type.isInterface();
            if (!isInterfaceType && !ENABLE_CGLIB) {
                throw new IllegalArgumentException("The proxy type must be an interface type while CGLIB is not enabled.");
            }
            boolean parsed = parseServiceMethod(type);
            if (parsed) {
                // prefer to use CGLIB
                if ((Objects.equals(ProxyStrategy.AUTO, proxyStrategy) ||
                        Objects.equals(ProxyStrategy.CGLIB, proxyStrategy)) && ENABLE_CGLIB) {
                    return newCglibProxy(type, tatget);
                }
                if (isInterfaceType) {
                    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, (proxy, method, args)
                            -> invokeMethodWithChance(tatget, method, args));
                }
            }
            if (!parsed) {
                throw new IllegalStateException(String.format("No chance annotations were found for type: %s.", type));
            }
            throw new IllegalStateException("Create proxy failed, maybe you should include the CGLIB dependency?");
        }

        private <T> boolean parseServiceMethod(Class<? super T> type) {
            ChanceFor typeAnno = type.getAnnotation(ChanceFor.class);
            boolean parsed = false;
            Class<?> clazz = type;
            if (clazz != Object.class) {
                if (clazz.isInterface()) {
                    parsed = parseMethodsWithType(typeAnno, clazz);
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> superInterface : interfaces) {
                        parsed = parsed || parseMethodsWithType(typeAnno, superInterface);
                    }
                } else {
                    do {
                        parsed = parsed || parseMethodsWithType(typeAnno, clazz);
                        clazz = clazz.getSuperclass();
                    }
                    while (Objects.nonNull(clazz) && clazz != Object.class);
                }
            }
            return parsed;
        }

        private boolean parseMethodsWithType(ChanceFor typeAnno, Class<?> type) {
            boolean parsed = false;
            Method[] declaredMethods = type.getDeclaredMethods();
            for (Method method : declaredMethods) {
                int modifiers = method.getModifiers();
                if (method.isBridge() || method.isDefault() || method.isSynthetic() || Modifier.isStatic(modifiers)) {
                    continue;
                }
                method.setAccessible(true);
                ChanceFor methodAnno = method.getAnnotation(ChanceFor.class);
                ChanceFor annoToUse = Optional.ofNullable(methodAnno).orElse(typeAnno);
                if (Objects.nonNull(annoToUse)) {
                    try {
                        Builder builder = Chance.newBuilder();
                        Class<? extends TimeLimiter> timeLimiterType = annoToUse.timeLimiter();
                        long timeout = annoToUse.timeout();
                        if (TimeLimiter.class != timeLimiterType && timeout > 0) {
                            TimeLimiter timeLimiter = Utils.U.newInstance(timeLimiterType);
                            builder.withTimeLimiter(timeLimiter, timeout, TimeUnit.MILLISECONDS);
                        }
                        int maxRetryTimes = annoToUse.maxRetryTimes();
                        if (maxRetryTimes > 0) {
                            builder.withMaxRetryTimes(maxRetryTimes);
                        } else if (maxRetryTimes == 0) {
                            builder.withNeverRetry();
                        } else {
                            builder.withRetryForever();
                        }
                        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
                        Class<? extends Throwable>[] includeExceptionTypes = annoToUse.include();
                        if (Objects.nonNull(includeExceptionTypes)) {
                            for (Class<? extends Throwable> i : includeExceptionTypes) {
                                retryableExceptions.put(i, Boolean.TRUE);
                            }
                        }
                        Class<? extends Throwable>[] excludeExceptionTypes = annoToUse.exclude();
                        if (Objects.nonNull(excludeExceptionTypes)) {
                            for (Class<? extends Throwable> e : excludeExceptionTypes) {
                                retryableExceptions.put(e, Boolean.FALSE);
                            }
                        }
                        if (!retryableExceptions.isEmpty()) {
                            builder.withRetryableExceptions(retryableExceptions);
                        }
                        Class<? extends Listener>[] listenerTypes = annoToUse.listeners();
                        if (Objects.nonNull(listenerTypes)) {
                            for (Class<? extends Listener> l : listenerTypes) {
                                Listener listener = Utils.U.newInstance(l);
                                builder.withListener(listener);
                            }
                        }
                        Class<? extends Recovery> recoveryType = annoToUse.recovery();
                        if (Recovery.class != recoveryType) {
                            Recovery recovery = Utils.U.newInstance(recoveryType);
                            builder.withRecovery(recovery);
                            builder.withRecoverIfHasException();
                        }
                        ChanceFor.BlockType blockType = annoToUse.blockType();
                        if (ChanceFor.BlockType.THREAD_SLEEP == blockType) {
                            builder.withBlocker(Blocker.newThreadSleepBlocker());
                        }
                        ChanceFor.WaitFor[] waits = annoToUse.waits();
                        if (Objects.nonNull(waits)) {
                            for (ChanceFor.WaitFor wf : waits) {
                                Class<? extends Wait> customWaitType = wf.custom();
                                if (Wait.class != customWaitType) {
                                    Wait wait = Utils.U.newInstance(customWaitType);
                                    builder.withWait(wait);
                                } else {
                                    long multiplier = wf.multiplier();
                                    long maxDuration = wf.maxDuration();
                                    double base = wf.base();
                                    switch (wf.type()) {
                                        case NO:
                                            builder.withWait(Wait.newNoWait());
                                            break;
                                        case FIXED:
                                            builder.withWait(Wait.newFixedWait(multiplier > 0 ? multiplier : maxDuration));
                                            break;
                                        case RANDOM:
                                            builder.withWait(Wait.newRandomWait(multiplier, maxDuration));
                                        case INCREMENTING:
                                            builder.withWait(Wait.newIncrementingWait(multiplier, maxDuration));
                                            break;
                                        case EXPONENTIAL:
                                            if (base > 0) {
                                                builder.withWait(Wait.newExponentialBackOffWait(base, multiplier, maxDuration));
                                            } else {
                                                builder.withWait(Wait.newExponentialBackOffWait(multiplier, maxDuration));
                                            }
                                            break;
                                        case FIBONACCI:
                                            builder.withWait(Wait.newFibonacciBackoffWait(multiplier, maxDuration));
                                            break;
                                        default: {

                                        }
                                    }
                                }
                            }
                        }
                        Chance chance = builder.build();
                        serviceMethodCache.putIfAbsent(method, chance);
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new IllegalArgumentException(e);
                    }
                    parsed = true;
                }
            }
            return parsed;
        }

        private <T> Object invokeMethodWithChance(T tatget, Method method, Object[] args)
                throws InvocationTargetException, IllegalAccessException, ChanceException {
            Object[] argsToUse = args != null ? args : new Object[0];
            Chance chanceToUse = serviceMethodCache.get(method);
            if (Objects.isNull(chanceToUse)) {
                return method.invoke(tatget, argsToUse);
            }
            return chanceToUse.call(() -> {
                try {
                    return method.invoke(tatget, argsToUse);
                } catch (Error | RuntimeException e) {
                    throw e;
                } catch (Throwable t) {
                    throw new ExecutionException(t);
                }
            });
        }

        private <T> T newCglibProxy(Class<? super T> type, T tatget) {
            net.sf.cglib.proxy.Enhancer enhancer = new net.sf.cglib.proxy.Enhancer();
            enhancer.setSuperclass(type);
            enhancer.setCallback((MethodInterceptor) (object, method, args, methodProxy)
                    -> invokeMethodWithChance(tatget, method, args));
            return (T) enhancer.create();
        }

        static {
            try {
                Class.forName("net.sf.cglib.proxy.Enhancer");
                ENABLE_CGLIB = true;
            } catch (Throwable ignore) {
                ENABLE_CGLIB = false;
            }
        }
    }
}
