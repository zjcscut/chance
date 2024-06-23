package cn.vlts.chance;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chance Test
 *
 * @author throwable
 * @since 2024/6/22 22:32
 */
public class ChanceTest {

    @Test
    public void testBuildSimpleChance() {
        Chance<String> chance = Chance.<String, Throwable>newBuilder()
                .withNeverRetry()
                .build();
        String result = null;
        try {
            result = chance.call(() -> "chance");
        } catch (Exception e) {
            // never reach
            assertThat(e).isNull();
        }
        assertThat(result).isEqualTo("chance");
    }

    @Test
    public void testWrapCallableChance() {
        Callable<String> callable = Chance.<String, Throwable>newBuilder()
                .withNeverRetry()
                .build()
                .wrap(() -> "chance");
        String result = null;
        try {
            result = callable.call();
        } catch (Exception e) {
            // never reach
            assertThat(e).isNull();
        }
        assertThat(result).isEqualTo("chance");
    }

    @Test
    public void testWrapRunnableChance() {
        Callable<String> callable = Chance.<String, Throwable>newBuilder()
                .withNeverRetry()
                .build()
                .wrap(() -> {
                    // do nothing
                }, "chance");
        String result = null;
        try {
            result = callable.call();
        } catch (Exception e) {
            // never reach
            assertThat(e).isNull();
        }
        assertThat(result).isEqualTo("chance");
    }

    @Test
    public void testCancelChance() {
        Chance<String> chance = Chance.<String, Throwable>newBuilder()
                .withRetryForever()
                .withListener(attempt -> {
                    if (attempt.attemptTimes() == 3) {
                        assertThat(attempt.cancel()).isEqualTo(true);
                        attempt.forceCancel();
                    }
                })
                .build();
        String result = null;
        try {
            result = chance.call(() -> {
                throw new RuntimeException("Error");
            });
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
        assertThat(result).isNull();
    }

    @Test
    public void testInterruptedChance() {
        Chance<String> chance = Chance.<String, Throwable>newBuilder()
                .withMaxRetryTimes(3)
                .withRetryableResult(r -> Objects.equals(r, "chance"))
                .withBlocker((blockMillis, attempt) -> {
                    throw new InterruptedException("Interrupted");
                }).build();
        assertThatThrownBy(() -> chance.call(() -> "chance"))
                .isInstanceOf(ChanceException.class)
                .hasMessageContaining("interrupted");
    }

    @Test
    public void testChanceListener() {
        try {
            Chance.<String, Throwable>newBuilder()
                    .withNeverRetry()
                    .withListener(null);
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class).hasMessage("Listener must not be null.");
        }
        try {
            Chance.<String, Throwable>newBuilder()
                    .withNeverRetry()
                    .withListener(attempt -> {
                        assertThat(attempt).isNotNull();
                        assertThat(attempt.attemptTimes()).isEqualTo(1);
                        assertThat(attempt.startNanos()).isGreaterThanOrEqualTo(0L);
                        assertThat(attempt.initialNanos()).isGreaterThanOrEqualTo(0L);
                        assertThat(attempt.completionNanos()).isGreaterThanOrEqualTo(0L);
                        if (attempt.hasResult()) {
                            assertThat(attempt.resultNow()).isNotNull();
                        } else {
                            assertThat(attempt.state()).isEqualTo(Attempt.State.RUNNING);
                        }
                    }).build().call(() -> "chance");
        } catch (Throwable e) {
            assertThat(e).isNull();
        }
        try {
            Chance.<String, Throwable>newBuilder()
                    .withNeverRetry()
                    .withListener(attempt -> {
                        if (attempt.hasException()) {
                            assertThat(attempt.exceptionNow()).isNotNull();
                        } else {
                            assertThat(attempt.state()).isEqualTo(Attempt.State.RUNNING);
                        }
                    }).build().call(() -> {
                        throw new RuntimeException("Error");
                    });
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(ChanceException.class);
            ChanceException chanceException = (ChanceException) e;
            assertThat(chanceException.isInterrupted()).isEqualTo(false);
            assertThat(chanceException.getAttempt()).isNotNull();
            assertThat(chanceException.getException()).isInstanceOf(RuntimeException.class);
            assertThat(chanceException.getCause()).isInstanceOf(RuntimeException.class);
            assertThatThrownBy(() -> chanceException.getAttempt().resultNow()).hasMessage("Invalid state, current state is not 'success'.");
            assertThat(chanceException.getAttempt().exceptionNow()).isInstanceOf(RuntimeException.class).hasMessage("Error");
        }
    }

    @Test
    public void testNoTimeLimiterChance() {
        Chance<String> chance = Chance.<String, Throwable>newBuilder()
                .withNeverRetry()
                .withTimeLimiter(TimeLimiter.newNoTimeLimiter(), 1, TimeUnit.MILLISECONDS)
                .build();
        String result = null;
        try {
            result = chance.call(() -> "chance");
        } catch (Exception e) {
            // never reach
            assertThat(e).isNull();
        }
        assertThat(result).isEqualTo("chance");
        try {
            chance.call(() -> {
                throw new RuntimeException("Error");
            });
        } catch (Exception e) {
            assertThat(e).isInstanceOf(ChanceException.class).cause().isInstanceOf(RuntimeException.class).hasMessage("Error");
        }
        try {
            chance.call(() -> {
                throw new Exception("Error");
            });
        } catch (Exception e) {
            assertThat(e).isInstanceOf(ChanceException.class)
                    .cause().isInstanceOf(Exception.class).hasMessage("Error");
        }
    }

    @Test
    public void testCompletableFutureTimeLimiterChance() {
        Chance<String> chance = Chance.<String, Throwable>newBuilder()
                .withNeverRetry()
                .withTimeLimiter(TimeLimiter.newCompletableFutureTimeLimiter(), 50, TimeUnit.MILLISECONDS)
                .build();
        try {
            chance.call(() -> {
                Thread.sleep(100);
                return "chance";
            });
        } catch (Exception e) {
            assertThat(e).isInstanceOf(ChanceException.class).cause().isInstanceOf(TimeoutException.class);
        }
        try {
            chance.call(() -> {
                throw new RuntimeException("Error");
            });
        } catch (Exception e) {
            assertThat(e).isInstanceOf(ChanceException.class)
                    .cause().isInstanceOf(RuntimeException.class).hasMessage("Error");
        }
        try {
            chance.call(() -> {
                throw new Exception("Error");
            });
        } catch (Exception e) {
            assertThat(e).isInstanceOf(ChanceException.class)
                    .cause().isInstanceOf(Exception.class).hasMessage("Error");
        }
    }

    @Test
    public void testCompletableFutureWithExecutorTimeLimiterChance() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Chance<String> chance = Chance.<String, Throwable>newBuilder()
                .withNeverRetry()
                .withTimeLimiter(TimeLimiter.newCompletableFutureTimeLimiter(executorService), 500, TimeUnit.MILLISECONDS)
                .build();
        try {
            chance.call(() -> {
                Thread.sleep(550);
                return "chance";
            });
        } catch (Exception e) {
            assertThat(e).isInstanceOf(ChanceException.class).cause().isInstanceOf(TimeoutException.class);
        }
        try {
            chance.call(() -> {
                throw new RuntimeException("Error");
            });
        } catch (Exception e) {
            assertThat(e).isInstanceOf(ChanceException.class).cause()
                    .isInstanceOf(RuntimeException.class).hasMessage("Error");
        }
        try {
            chance.call(() -> {
                throw new Exception("Error");
            });
        } catch (Exception e) {
            assertThat(e).isInstanceOf(ChanceException.class)
                    .cause().isInstanceOf(Exception.class).hasMessage("Error");
        }
        executorService.shutdown();
    }

    @Test
    public void testNewExecutorServiceTimeLimiterChance() {
        Chance<String> chance = Chance.<String, Throwable>newBuilder()
                .withNeverRetry()
                .withTimeLimiter(TimeLimiter.newExecutorServiceTimeLimiter(Executors.newSingleThreadExecutor()), 50, TimeUnit.MILLISECONDS)
                .build();
        try {
            chance.call(() -> {
                Thread.sleep(100);
                return "chance";
            });
        } catch (Exception e) {
            assertThat(e).isInstanceOf(ChanceException.class).cause().isInstanceOf(TimeoutException.class);
        }
    }

    @Test
    public void testInvalidTimeLimiterParameters() {
        TimeLimiter<String> tl = TimeLimiter.newExecutorServiceTimeLimiter(Executors.newSingleThreadExecutor());
        try {
            Chance.<String, Throwable>newBuilder()
                    .withTimeLimiter(null, 50, TimeUnit.MILLISECONDS)
                    .build();
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class).hasMessage("TimeLimiter must not be null.");
        }
        try {
            Chance.<String, Throwable>newBuilder()
                    .withTimeLimiter(tl, 0, TimeUnit.MILLISECONDS)
                    .build();
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class).hasMessage("TimeLimitDuration must be greater than 0.");
        }
        try {
            Chance.<String, Throwable>newBuilder()
                    .withTimeLimiter(tl, 1, null)
                    .build();
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class).hasMessage("TimeLimitUnit must not be null.");
        }
    }

    @Test
    public void testInvalidBlockerParameters() {
        try {
            Chance.<String, Throwable>newBuilder()
                    .withBlocker(null)
                    .build();
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class).hasMessage("Blocker must not be null.");
        }
    }

    @Test
    public void testThreadSleepBlocker() {
        try {
            Chance.<String, Throwable>newBuilder()
                    .withMaxRetryTimes(2)
                    .withFixedWaitTime(10, TimeUnit.MILLISECONDS)
                    .withBlocker(Blocker.newThreadSleepBlocker())
                    .build()
                    .call(() -> {
                        throw new RuntimeException("Retry");
                    });
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(ChanceException.class)
                    .cause()
                    .isInstanceOf(RuntimeException.class).hasMessage("Retry");
            ChanceException chanceException = (ChanceException) e;
            assertThat(chanceException.getAttempt().attemptTimes()).isEqualTo(2);
            assertThat(chanceException.getAttempt().completionNanos() - chanceException.getAttempt().initialNanos())
                    .isGreaterThan(10);
        }
    }

    @Test
    public void testObjectWaitBlocker() {
        try {
            Chance.<String, Throwable>newBuilder()
                    .withMaxRetryTimes(2)
                    .withFixedWaitTime(10, TimeUnit.MILLISECONDS)
                    .withBlocker(Blocker.newObjectWaitBlocker())
                    .build()
                    .call(() -> {
                        throw new RuntimeException("Retry");
                    });
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(ChanceException.class)
                    .cause()
                    .isInstanceOf(RuntimeException.class).hasMessage("Retry");
            ChanceException chanceException = (ChanceException) e;
            assertThat(chanceException.getAttempt().attemptTimes()).isEqualTo(2);
            assertThat(chanceException.getAttempt().completionNanos() - chanceException.getAttempt().initialNanos())
                    .isGreaterThan(10);
        }
    }

    @Test
    public void testLockSupportParkBlocker() {
        try {
            Chance.<String, Throwable>newBuilder()
                    .withMaxRetryTimes(2)
                    .withFixedWaitTime(10, TimeUnit.MILLISECONDS)
                    .withBlocker(Blocker.newLockSupportParkBlocker())
                    .build()
                    .call(() -> {
                        throw new RuntimeException("Retry");
                    });
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(ChanceException.class)
                    .cause()
                    .isInstanceOf(RuntimeException.class).hasMessage("Retry");
            ChanceException chanceException = (ChanceException) e;
            assertThat(chanceException.getAttempt().attemptTimes()).isEqualTo(2);
            assertThat(chanceException.getAttempt().completionNanos() - chanceException.getAttempt().initialNanos())
                    .isGreaterThan(10);
        }
    }

    @Test
    public void testChanceCompositeChoice() {
        try {
            Chance.<String, Throwable>newBuilder()
                    .withNeverRetry()
                    .withChoice(null);
        } catch (Throwable e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class).hasMessage("Choice must not be null.");
        }
        Choice<String, Throwable> foreverChoice = Choice.newForeverChoice();
        Choice<String, Throwable> neverChoice = Choice.newNeverChoice();
        Choice<String, Throwable> cancelJudgeChoice = Choice.newCancelJudgeChoice();
        Choice<String, Throwable> maxTimesLimitChoice = Choice.newMaxTimesLimitChoice(3);
        Map<Class<? extends Throwable>, Boolean> map = new HashMap<>();
        map.put(RuntimeException.class, Boolean.TRUE);
        Choice<String, Throwable> exceptionTypeMapChoice = Choice.newExceptionTypeMapChoice(map);
        Choice<String, Throwable> resultPredicateChoice = Choice.newResultPredicateListChoice(
                Collections.singletonList(s -> Objects.equals("chance", s)));
        Choice<String, Throwable> maxCallingTimeLimitChoice = Choice.newMaxCallingTimeLimitChoice(
                1000);
        Chance<String> chance = Chance.<String, Throwable>newBuilder()
                .withNeverRetry()
                .withChoice(foreverChoice)
                .withChoice(neverChoice)
                .withChoice(cancelJudgeChoice)
                .withChoice(maxTimesLimitChoice)
                .withChoice(exceptionTypeMapChoice)
                .withChoice(resultPredicateChoice)
                .withChoice(maxCallingTimeLimitChoice)
                .build();
        assertThat(chance).extracting("choice").isNotNull().isInstanceOf(Choice.CompositeChoice.class)
                .extracting("choiceList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains(foreverChoice, neverChoice, cancelJudgeChoice, maxTimesLimitChoice, exceptionTypeMapChoice,
                        resultPredicateChoice, maxCallingTimeLimitChoice);
        try {
            chance.call(() -> "chance");
        } catch (Exception e) {
            assertThat(e).isNull();
        }
        try {
            chance.call(() -> {
                throw new RuntimeException("Error");
            });
        } catch (Exception e) {
            assertThat(e).isNotNull();
        }
        assertThatThrownBy(() -> Choice.newMaxTimesLimitChoice(0)).hasMessage("MaxTimes must be greater than 0.");
        assertThatThrownBy(() -> Choice.newMaxCallingTimeLimitChoice(0)).hasMessage("MaxDuration must be greater than 0.");
        assertThatThrownBy(() -> Choice.newCompositeChoice(new Choice[0])).hasMessage("ChoiceList must not be empty.");
    }

    @Test
    public void testChanceCompositeWait() {
        Wait<String, Throwable> noWait = Wait.newNoWait();
        Wait<String, Throwable> fixedWait = Wait.newFixedWait(1);
        Wait<String, Throwable> randomWait = Wait.newRandomWait(1, 1);
        Wait<String, Throwable> incrementingWait = Wait.newIncrementingWait(1, 1);
        Wait<String, Throwable> exponentialBackOffWait = Wait.newExponentialBackOffWait(1, 2);
        Wait<String, Throwable> fibonacciBackoffWait = Wait.newFibonacciBackoffWait(1, 2);
        Chance<String> chance = Chance.<String, Throwable>newBuilder()
                .withNeverRetry()
                .withWait(noWait)
                .withWait(fixedWait)
                .withWait(randomWait)
                .withWait(incrementingWait)
                .withWait(exponentialBackOffWait)
                .withWait(fibonacciBackoffWait)
                .build();
        assertThat(chance).extracting("wait").isNotNull().isInstanceOf(Wait.CompositeWait.class)
                .extracting("waitList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains(noWait, fixedWait, randomWait, incrementingWait, exponentialBackOffWait, fibonacciBackoffWait);
        assertThatThrownBy(() -> Wait.newFixedWait(0)).hasMessage("Duration must be greater than 0.");
        assertThatThrownBy(() -> Wait.newRandomWait(0, 1)).hasMessage("MinDuration must be greater than 0.");
        assertThatThrownBy(() -> Wait.newRandomWait(1, 0)).hasMessage("MaxDuration must be greater than minDuration.");
        assertThatThrownBy(() -> Wait.newIncrementingWait(-1, 0)).hasMessage("InitialDuration must be greater than 0 or equal to 0.");
        assertThatThrownBy(() -> Wait.newIncrementingWait(0, 0)).hasMessage("IncreasedDuration must be greater than 0.");
        assertThatThrownBy(() -> Wait.newExponentialBackOffWait(0, 0, 0)).hasMessage("Base must be greater than 0.");
        assertThatThrownBy(() -> Wait.newExponentialBackOffWait(0, 0)).hasMessage("Multiplier must be greater than 0.");
        assertThatThrownBy(() -> Wait.newExponentialBackOffWait(1, 0)).hasMessage("MaxDuration must be greater than 0.");
        assertThatThrownBy(() -> Wait.newExponentialBackOffWait(1, 1)).hasMessage("MaxDuration must be greater than multiplier.");
        assertThatThrownBy(() -> Wait.newFibonacciBackoffWait(0, 0)).hasMessage("Multiplier must be greater than 0.");
        assertThatThrownBy(() -> Wait.newFibonacciBackoffWait(1, 0)).hasMessage("MaxDuration must be greater than 0.");
        assertThatThrownBy(() -> Wait.newFibonacciBackoffWait(1, 1)).hasMessage("MaxDuration must be greater than multiplier.");
        assertThatThrownBy(() -> Wait.newCompositeWait(new Wait[0])).hasMessage("WaitList must not be empty.");
    }

    @Test
    public void testChanceRecovery() {
        Chance<String> chance = Chance.<String, Throwable>newBuilder()
                .withNeverRetry()
                .withRecoverPredicate(((recovery, attempt) -> attempt.hasException()))
                .withRecovery(attempt -> "recovery")
                .build();
        String result = null;
        try {
            result = chance.call(() -> {
                throw new RuntimeException("Error");
            });
        } catch (Exception e) {
            assertThat(e).isInstanceOf(ChanceException.class).cause().hasMessage("Error");
        }
        assertThat(result).isEqualTo("recovery");
    }

    @Test
    public void testWaits() {
        CustomAttempt<String> attempt = new CustomAttempt<>();
        // noWait
        assertThat(Wait.<String, Throwable>newNoWait().computeWaitDuration(attempt)).isEqualTo(0L);
        // fixedWait
        assertThat(Wait.<String, Throwable>newFixedWait(1000L).computeWaitDuration(attempt)).isEqualTo(1000L);
        // randomWait
        assertThat(Wait.<String, Throwable>newRandomWait(1000L, 2000L).computeWaitDuration(attempt))
                .isGreaterThanOrEqualTo(1000L).isLessThan(2000L);
        // incrementingWait
        attempt.setAttemptTimes(1);
        assertThat(Wait.<String, Throwable>newIncrementingWait(1000L, 2000L).computeWaitDuration(attempt))
                .isEqualTo(1000L);
        attempt.setAttemptTimes(2);
        assertThat(Wait.<String, Throwable>newIncrementingWait(1000L, 2000L).computeWaitDuration(attempt))
                .isEqualTo(3000L);
        // exponentialBackOffWait
        attempt.setAttemptTimes(1);
        assertThat(Wait.<String, Throwable>newExponentialBackOffWait(1000L, 5000L).computeWaitDuration(attempt))
                .isEqualTo(2000L);
        attempt.setAttemptTimes(2);
        assertThat(Wait.<String, Throwable>newExponentialBackOffWait(1000L, 5000L).computeWaitDuration(attempt))
                .isEqualTo(4000L);
        attempt.setAttemptTimes(3);
        assertThat(Wait.<String, Throwable>newExponentialBackOffWait(1000L, 5000L).computeWaitDuration(attempt))
                .isEqualTo(5000L);
        // fibonacciBackoffWait
        attempt.setAttemptTimes(1);
        assertThat(Wait.<String, Throwable>newFibonacciBackoffWait(1000L, 5000L).computeWaitDuration(attempt))
                .isEqualTo(0L);
        attempt.setAttemptTimes(2);
        assertThat(Wait.<String, Throwable>newFibonacciBackoffWait(1000L, 5000L).computeWaitDuration(attempt))
                .isEqualTo(1000L);
        attempt.setAttemptTimes(3);
        assertThat(Wait.<String, Throwable>newFibonacciBackoffWait(1000L, 5000L).computeWaitDuration(attempt))
                .isEqualTo(1000L);
        attempt.setAttemptTimes(4);
        assertThat(Wait.<String, Throwable>newFibonacciBackoffWait(1000L, 5000L).computeWaitDuration(attempt))
                .isEqualTo(2000L);
        attempt.setAttemptTimes(5);
        assertThat(Wait.<String, Throwable>newFibonacciBackoffWait(1000L, 5000L).computeWaitDuration(attempt))
                .isEqualTo(3000L);
        attempt.setAttemptTimes(6);
        assertThat(Wait.<String, Throwable>newFibonacciBackoffWait(1000L, 5000L).computeWaitDuration(attempt))
                .isEqualTo(5000L);
        attempt.setAttemptTimes(7);
        assertThat(Wait.<String, Throwable>newFibonacciBackoffWait(1000L, 5000L).computeWaitDuration(attempt))
                .isEqualTo(5000L);
        // compositeWait
        attempt.setAttemptTimes(7);
        Wait<String, Throwable> w1 = Wait.newFibonacciBackoffWait(1000L, 5000L);
        Wait<String, Throwable> w2 = Wait.newFixedWait(1000L);
        assertThat(Wait.newCompositeWait(w1, w2).computeWaitDuration(attempt))
                .isEqualTo(6000L);
    }

    @Test
    public void testChoices() {
        CustomAttempt<String> attempt = new CustomAttempt<>();
        // foreverChoice
        assertThat(Choice.<String, Throwable>newForeverChoice().shouldRetryNext(attempt)).isEqualTo(true);
        // neverChoice
        assertThat(Choice.<String, Throwable>newNeverChoice().shouldRetryNext(attempt)).isEqualTo(false);
        // cancelJudgeChoice
        attempt.setState(Attempt.State.CANCELLED);
        assertThat(Choice.<String, Throwable>newCancelJudgeChoice().shouldRetryNext(attempt)).isEqualTo(false);
        attempt.setState(Attempt.State.RUNNING);
        assertThat(Choice.<String, Throwable>newCancelJudgeChoice().shouldRetryNext(attempt)).isEqualTo(true);
        // maxTimesLimitChoice
        attempt.setAttemptTimes(2);
        assertThat(Choice.<String, Throwable>newMaxTimesLimitChoice(2).shouldRetryNext(attempt)).isEqualTo(false);
        assertThat(Choice.<String, Throwable>newMaxTimesLimitChoice(3).shouldRetryNext(attempt)).isEqualTo(true);
        // exceptionTypeMapChoice
        Map<Class<? extends Throwable>, Boolean> map = new HashMap<>();
        map.put(RuntimeException.class, Boolean.TRUE);
        map.put(IllegalArgumentException.class, Boolean.FALSE);
        assertThat(Choice.<String, Throwable>newExceptionTypeMapChoice(map).shouldRetryNext(attempt)).isEqualTo(true);
        attempt.setThrowable(new RuntimeException("Error"));
        assertThat(Choice.<String, Throwable>newExceptionTypeMapChoice(map).shouldRetryNext(attempt)).isEqualTo(true);
        attempt.setThrowable(new IllegalArgumentException("Error"));
        assertThat(Choice.<String, Throwable>newExceptionTypeMapChoice(map).shouldRetryNext(attempt)).isEqualTo(false);
        attempt.setThrowable(new IllegalStateException("Error"));
        assertThat(Choice.<String, Throwable>newExceptionTypeMapChoice(map).shouldRetryNext(attempt)).isEqualTo(true);
        // --- clear attempt ---
        attempt.setRunning();
        // resultPredicateListChoice
        List<Predicate<String>> resultPredicateList = new ArrayList<>();
        resultPredicateList.add(s -> Objects.equals(s, "retry"));
        assertThat(Choice.newResultPredicateListChoice(resultPredicateList).shouldRetryNext(attempt)).isEqualTo(true);
        attempt.setResult("retry");
        assertThat(Choice.newResultPredicateListChoice(resultPredicateList).shouldRetryNext(attempt)).isEqualTo(true);
        attempt.setResult("success");
        assertThat(Choice.newResultPredicateListChoice(resultPredicateList).shouldRetryNext(attempt)).isEqualTo(false);
        // maxCallingTimeLimitChoice
        attempt.setInitialNanos(TimeUnit.MILLISECONDS.toNanos(0L));
        attempt.setCompletionNanos(TimeUnit.MILLISECONDS.toNanos(500L));
        assertThat(Choice.<String, Throwable>newMaxCallingTimeLimitChoice(1000L).shouldRetryNext(attempt)).isEqualTo(true);
        attempt.setCompletionNanos(TimeUnit.MILLISECONDS.toNanos(1001L));
        assertThat(Choice.<String, Throwable>newMaxCallingTimeLimitChoice(1000L).shouldRetryNext(attempt)).isEqualTo(false);
        // --- clear attempt ---
        attempt.setRunning();
        // compositeChoice
        Choice<String, Throwable> c1 = Choice.newForeverChoice();
        Choice<String, Throwable> c2 = Choice.newMaxTimesLimitChoice(3);
        Choice<String, Throwable> c3 = Choice.newNeverChoice();
        attempt.setAttemptTimes(1);
        assertThat(Choice.newCompositeChoice(c1, c2).shouldRetryNext(attempt)).isEqualTo(true);
        assertThat(Choice.newCompositeChoice(c2, c3).shouldRetryNext(attempt)).isEqualTo(false);
        attempt.setAttemptTimes(3);
        assertThat(Choice.newCompositeChoice(c1, c2).shouldRetryNext(attempt)).isEqualTo(false);
        assertThat(Choice.newCompositeChoice(c2, c3).shouldRetryNext(attempt)).isEqualTo(false);
    }

    @Test
    public void testChanceBuilderBuildInMethods() {
        // raw
        Chance<String> chance = Chance.<String, Throwable>newBuilder().build();
        assertThat(chance).extracting("wait").isInstanceOf(Wait.NoWait.class);
        assertThat(chance).extracting("choice").isInstanceOf(Choice.CompositeChoice.class)
                .extracting("choiceList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasOnlyElementsOfType(Choice.CancelJudgeChoice.class);
        // listener
        DefaultListener listener = new DefaultListener();
        chance = Chance.<String, Throwable>newBuilder().withListener(listener).build();
        assertThat(chance).extracting("listeners").asInstanceOf(InstanceOfAssertFactories.LIST)
                .contains(listener);
        // timeLimiter
        TimeLimiter<String> timeLimiter = TimeLimiter.newNoTimeLimiter();
        chance = Chance.<String, Throwable>newBuilder().withTimeLimiter(timeLimiter, 1, TimeUnit.MILLISECONDS).build();
        assertThat(chance).extracting("timeLimiter").isEqualTo(timeLimiter);
        // blocker
        Blocker<String, Throwable> blocker = Blocker.newThreadSleepBlocker();
        chance = Chance.<String, Throwable>newBuilder().withBlocker(blocker).build();
        assertThat(chance).extracting("blocker").isEqualTo(blocker);
        // custom choice
        Choice<String, Throwable> choice = Choice.newNeverChoice();
        chance = Chance.<String, Throwable>newBuilder().withChoice(choice).build();
        assertThat(chance).extracting("choice").isInstanceOf(Choice.CompositeChoice.class)
                .extracting("choiceList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(2)
                .hasAtLeastOneElementOfType(Choice.CancelJudgeChoice.class)
                .contains(choice);
        // custom wait - only 1
        Wait<String, Throwable> wait = Wait.newNoWait();
        chance = Chance.<String, Throwable>newBuilder().withWait(wait).build();
        assertThat(chance).extracting("wait").isInstanceOf(Wait.NoWait.class).isEqualTo(wait);
        // custom wait
        Wait<String, Throwable> w1 = Wait.newNoWait();
        Wait<String, Throwable> w2 = Wait.newFixedWait(1000L);
        chance = Chance.<String, Throwable>newBuilder().withWait(w1).withWait(w2).build();
        assertThat(chance).extracting("wait").isInstanceOf(Wait.CompositeWait.class)
                .extracting("waitList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(2)
                .contains(w1, w2);
        // fixedWaitTime
        chance = Chance.<String, Throwable>newBuilder().withFixedWaitTime(1000L, TimeUnit.MILLISECONDS).build();
        assertThat(chance).extracting("wait").isInstanceOf(Wait.FixedWait.class)
                .extracting("duration").isEqualTo(1000L);
        // randomWaitTime
        chance = Chance.<String, Throwable>newBuilder().withRandomWaitTime(1000L, 3000L, TimeUnit.MILLISECONDS).build();
        assertThat(chance).extracting("wait").isInstanceOf(Wait.RandomWait.class)
                .extracting("maxDuration").isEqualTo(3000L);
        // incrementingWaitTime
        chance = Chance.<String, Throwable>newBuilder().withIncrementingWaitTime(1000L, 2000L, TimeUnit.MILLISECONDS).build();
        assertThat(chance).extracting("wait").isInstanceOf(Wait.IncrementingWait.class)
                .extracting("increasedDuration").isEqualTo(2000L);
        // exponentialWaitTime
        chance = Chance.<String, Throwable>newBuilder().withExponentialWaitTime(1000L, 2000L, TimeUnit.MILLISECONDS).build();
        assertThat(chance).extracting("wait").isInstanceOf(Wait.ExponentialBackOffWait.class)
                .extracting("maxDuration").isEqualTo(2000L);
        chance = Chance.<String, Throwable>newBuilder().withExponentialWaitTime(3D, 1000L, 2000L, TimeUnit.MILLISECONDS).build();
        assertThat(chance).extracting("wait").isInstanceOf(Wait.ExponentialBackOffWait.class)
                .extracting("base").isEqualTo(3D);
        // fibonacciWaitTime
        chance = Chance.<String, Throwable>newBuilder().withFibonacciWaitTime(1000L, 3000L, TimeUnit.MILLISECONDS).build();
        assertThat(chance).extracting("wait").isInstanceOf(Wait.FibonacciBackoffWait.class)
                .extracting("maxDuration").isEqualTo(3000L);
        // maxRetryTimes
        chance = Chance.<String, Throwable>newBuilder().withMaxRetryTimes(100).build();
        assertThat(chance).extracting("choice").isInstanceOf(Choice.CompositeChoice.class)
                .extracting("choiceList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(2)
                .hasAtLeastOneElementOfType(Choice.CancelJudgeChoice.class)
                .last().extracting("maxTimes").isEqualTo(100);
        // retryForever
        chance = Chance.<String, Throwable>newBuilder().withRetryForever().build();
        assertThat(chance).extracting("choice").isInstanceOf(Choice.CompositeChoice.class)
                .extracting("choiceList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(2)
                .hasAtLeastOneElementOfType(Choice.CancelJudgeChoice.class)
                .last().isInstanceOf(Choice.ForeverChoice.class);
        // neverRetry
        chance = Chance.<String, Throwable>newBuilder().withNeverRetry().build();
        assertThat(chance).extracting("choice").isInstanceOf(Choice.CompositeChoice.class)
                .extracting("choiceList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(2)
                .hasAtLeastOneElementOfType(Choice.CancelJudgeChoice.class)
                .last().isInstanceOf(Choice.NeverChoice.class);
        // maxCallingDuration
        chance = Chance.<String, Throwable>newBuilder().withMaxCallingDuration(1000L, TimeUnit.MILLISECONDS).build();
        assertThat(chance).extracting("choice").isInstanceOf(Choice.CompositeChoice.class)
                .extracting("choiceList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(2)
                .hasAtLeastOneElementOfType(Choice.CancelJudgeChoice.class)
                .last().extracting("maxDuration").isEqualTo(1000L);
        // retryableExceptions
        Map<Class<? extends Throwable>, Boolean> exceptionMap = new HashMap<>();
        exceptionMap.put(RuntimeException.class, Boolean.TRUE);
        exceptionMap.put(IllegalArgumentException.class, Boolean.FALSE);
        chance = Chance.<String, Throwable>newBuilder().withRetryableExceptions(exceptionMap).build();
        assertThat(chance).extracting("choice").isInstanceOf(Choice.CompositeChoice.class)
                .extracting("choiceList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(2)
                .hasAtLeastOneElementOfType(Choice.CancelJudgeChoice.class)
                .last().isInstanceOf(Choice.ExceptionTypeMapChoice.class)
                .extracting("exceptionTypeMap").asInstanceOf(InstanceOfAssertFactories.MAP)
                .hasSize(2)
                .containsAllEntriesOf(exceptionMap);
        // retryableExceptions
        List<Predicate<String>> retryableResults = new ArrayList<>();
        retryableResults.add(s -> Objects.equals(s, "retry"));
        chance = Chance.<String, Throwable>newBuilder().withRetryableResults(retryableResults).build();
        assertThat(chance).extracting("choice").isInstanceOf(Choice.CompositeChoice.class)
                .extracting("choiceList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(2)
                .hasAtLeastOneElementOfType(Choice.CancelJudgeChoice.class)
                .last().isInstanceOf(Choice.ResultPredicateListChoice.class)
                .extracting("resultPredicateList").asInstanceOf(InstanceOfAssertFactories.LIST)
                .hasSize(1)
                .containsAll(retryableResults);
        chance = Chance.<String, Throwable>newBuilder().withAttemptPredicate(attempt -> attempt.attemptTimes() < 100).build();
        assertThat(chance).extracting("attemptPredicate").isNotNull();
    }

    @Test
    public void testChanceJdkProxy() {
        SomeApi someApi = Chance.newProxy(SomeApi.class, new SomeApiImpl(), Chance.ProxyStrategy.JDK);
        assertThat(someApi.useTypeAnnotation()).isEqualTo("useTypeAnnotation");
        assertThat(someApi.useMethodAnnotation()).isEqualTo("useMethodAnnotation");
    }

    @Test
    public void testChanceCglibProxy() {
        // proxy none interface
        SomeApiImpl someApiImpl = Chance.newProxy(SomeApiImpl.class, new SomeApiImpl());
        assertThat(someApiImpl.useTypeAnnotation()).isEqualTo("useTypeAnnotation");
        assertThat(someApiImpl.useMethodAnnotation()).isEqualTo("useMethodAnnotation");
        assertThat(someApiImpl.recover()).isEqualTo("Recovery");
        // proxy interface
        SomeApi someApi = Chance.newProxy(SomeApi.class, new SomeApiImpl());
        assertThat(someApi.useTypeAnnotation()).isEqualTo("useTypeAnnotation");
        assertThat(someApi.useMethodAnnotation()).isEqualTo("useMethodAnnotation");
        // wait test
        assertThatThrownBy(someApiImpl::fixedWait).rootCause().isInstanceOf(RuntimeException.class).hasMessageContaining("fixedWait");
        assertThatThrownBy(someApiImpl::randomWait).rootCause().isInstanceOf(RuntimeException.class).hasMessageContaining("randomWait");
        assertThatThrownBy(someApiImpl::incrementingWait).rootCause().isInstanceOf(RuntimeException.class).hasMessageContaining("incrementingWait");
        assertThatThrownBy(someApiImpl::exponentialWait).rootCause().isInstanceOf(RuntimeException.class).hasMessageContaining("exponentialWait");
        assertThatThrownBy(someApiImpl::fibonacciWait).rootCause().isInstanceOf(RuntimeException.class).hasMessageContaining("fibonacciWait");
        // include
        assertThatThrownBy(someApiImpl::include).isInstanceOf(ChanceException.class).hasMessageContaining("3 attempts");
        // exclude
        assertThatThrownBy(someApiImpl::exclude).isInstanceOf(ChanceException.class).hasMessageContaining("1 attempts");
    }

    @Test
    public void testChanceCglibProxyWithInvalidListener() {
        assertThatThrownBy(() -> Chance.newProxy(InvalidListenerApi.class, new InvalidListenerApi()))
                .isNotNull()
                .isInstanceOf(IllegalArgumentException.class);
    }

    static class DefaultListener implements Listener<String, Throwable> {

        @Override
        public void onFire(Attempt<String, Throwable> attempt) {

        }
    }

    static class DefaultRecovery implements Recovery<String> {

        @Override
        public String recover(Attempt<String, ?> attempt) {
            return "Recovery";
        }
    }

    @ChanceFor(maxRetryTimes = 0,
            timeLimiter = TimeLimiter.NoTimeLimiter.class,
            waits = @ChanceFor.WaitFor(type = ChanceFor.WaitType.NO),
            listeners = {DefaultListener.class})
    interface SomeApi {

        String useTypeAnnotation();

        @ChanceFor(maxRetryTimes = 1, waits = @ChanceFor.WaitFor(type = ChanceFor.WaitType.NO),
                listeners = {DefaultListener.class})
        String useMethodAnnotation();
    }

    @ChanceFor(maxRetryTimes = 0, waits = @ChanceFor.WaitFor(type = ChanceFor.WaitType.NO))
    static class SomeApiImpl implements SomeApi {

        @Override
        public String useTypeAnnotation() {
            return "useTypeAnnotation";
        }

        @Override
        @ChanceFor(maxRetryTimes = 1, waits = @ChanceFor.WaitFor(type = ChanceFor.WaitType.NO))
        public String useMethodAnnotation() {
            return "useMethodAnnotation";
        }

        @ChanceFor(maxRetryTimes = 2, waits = @ChanceFor.WaitFor(type = ChanceFor.WaitType.FIXED, multiplier = 100))
        public String fixedWait() {
            throw new RuntimeException("fixedWait");
        }

        @ChanceFor(maxRetryTimes = 2, waits = @ChanceFor.WaitFor(type = ChanceFor.WaitType.RANDOM,
                multiplier = 100, maxDuration = 300))
        public String randomWait() {
            throw new RuntimeException("randomWait");
        }

        @ChanceFor(maxRetryTimes = 2, waits = @ChanceFor.WaitFor(type = ChanceFor.WaitType.INCREMENTING,
                multiplier = 100, maxDuration = 300))
        public String incrementingWait() {
            throw new RuntimeException("incrementingWait");
        }

        @ChanceFor(maxRetryTimes = 2, waits = @ChanceFor.WaitFor(type = ChanceFor.WaitType.EXPONENTIAL,
                base = 2, multiplier = 100, maxDuration = 300))
        public String exponentialWait() {
            throw new RuntimeException("exponentialWait");
        }

        @ChanceFor(maxRetryTimes = 4, waits = @ChanceFor.WaitFor(type = ChanceFor.WaitType.FIBONACCI,
                multiplier = 100, maxDuration = 300))
        public String fibonacciWait() {
            throw new RuntimeException("fibonacciWait");
        }

        @ChanceFor(maxRetryTimes = 2, waits = @ChanceFor.WaitFor(custom = Wait.NoWait.class))
        public String customWait() {
            return "customWait";
        }

        @ChanceFor(maxRetryTimes = 2, recovery = DefaultRecovery.class)
        public String recover() {
            throw new RuntimeException("Error");
        }

        @ChanceFor(maxRetryTimes = 3, include = RuntimeException.class)
        public String include() {
            throw new RuntimeException("Error");
        }

        @ChanceFor(maxRetryTimes = 3, exclude = RuntimeException.class)
        public String exclude() {
            throw new RuntimeException("Error");
        }
    }

    static class InvalidListener implements Listener<String, Throwable> {

        public InvalidListener() {
            throw new RuntimeException("Error");
        }

        @Override
        public void onFire(Attempt<String, Throwable> attempt) {

        }
    }

    @ChanceFor(maxRetryTimes = 1, listeners = InvalidListener.class)
    static class InvalidListenerApi {

        public String foo() {
            return "bar";
        }
    }

    static class CustomAttempt<T> implements Attempt<T, Throwable> {

        private State state;

        private T result;

        private Throwable throwable;

        private int attemptTimes;

        private long initialNanos;

        private long startNanos;

        private long completionNanos;

        @Override
        public State state() {
            return state;
        }

        @Override
        public boolean cancel() {
            state = State.CANCELLED;
            return true;
        }

        @Override
        public void forceCancel() {
            state = State.CANCELLED;
        }

        @Override
        public T resultNow() {
            return result;
        }

        @Override
        public Throwable exceptionNow() {
            return throwable;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <S extends Throwable> S rootCauseNow() {
            return (S) Utils.U.getRootCause(throwable);
        }

        @Override
        public int attemptTimes() {
            return attemptTimes;
        }

        @Override
        public long initialNanos() {
            return initialNanos;
        }

        @Override
        public long startNanos() {
            return startNanos;
        }

        @Override
        public long completionNanos() {
            return completionNanos;
        }

        public void setState(State state) {
            this.state = state;
        }

        public void setResult(T result) {
            this.state = State.SUCCESS;
            this.result = result;
        }

        public void setThrowable(Throwable throwable) {
            this.state = State.FAILED;
            this.throwable = throwable;
        }

        public void setAttemptTimes(int attemptTimes) {
            this.attemptTimes = attemptTimes;
        }

        public void setInitialNanos(long initialNanos) {
            this.initialNanos = initialNanos;
        }

        public void setStartNanos(long startNanos) {
            this.startNanos = startNanos;
        }

        public void setCompletionNanos(long completionNanos) {
            this.completionNanos = completionNanos;
        }

        public void setRunning() {
            this.state = State.RUNNING;
            this.result = null;
            this.throwable = null;
        }
    }
}
