package cn.vlts.chance;

import java.lang.annotation.*;

/**
 * Annotation to config chance for type or method. The annotated method or all methods of the annotated type will be
 * wrapped as a chance to call.
 *
 * @author throwable
 * @since 2024/6/21 10:44
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ChanceFor {

    /**
     * Timeout in milliseconds for time limiter.
     */
    long timeout() default 0;

    /**
     * Time limiter class. See {@link TimeLimiter}.
     */
    Class<? extends TimeLimiter> timeLimiter() default TimeLimiter.class;

    /**
     * Max retry times, default value is 3.
     */
    int maxRetryTimes() default 3;

    /**
     * Throwable classes which the method should be retried.
     */
    Class<? extends Throwable>[] include() default {};

    /**
     * Throwable classes which the method should not be retried.
     */
    Class<? extends Throwable>[] exclude() default {};

    /**
     * One or more wait config.
     */
    WaitFor[] waits() default {};

    /**
     * One or more listener class.
     */
    Class<? extends Listener>[] listeners() default {};

    /**
     * Recovery class.
     */
    Class<? extends Recovery> recovery() default Recovery.class;

    /**
     * Block type, default value is THREAD_SLEEP.
     */
    BlockType blockType() default BlockType.THREAD_SLEEP;

    /**
     * Block type enum.
     */
    enum BlockType {

        /**
         * Thread sleep block type.
         */
        THREAD_SLEEP
    }

    /**
     * Wait type enum.
     */
    enum WaitType {

        /**
         * No wait, see {@link Wait#newNoWait()}
         */
        NO,

        /**
         * Fixed wait, see {@link Wait#newFixedWait(long)}
         */
        FIXED,

        /**
         * Random wait, see {@link Wait#newRandomWait(long, long)}
         */
        RANDOM,

        /**
         * Incrementing wait, see {@link Wait#newIncrementingWait(long, long)}
         */
        INCREMENTING,

        /**
         * Exponential wait, see {@link Wait#newExponentialBackOffWait(long, long)}
         */
        EXPONENTIAL,

        /**
         * Fibonacci wait, see {@link Wait#newFibonacciBackoffWait(long, long)}
         */
        FIBONACCI
    }

    /**
     * Wait for annotation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface WaitFor {

        /**
         * See {@link WaitType}
         */
        WaitType type() default WaitType.NO;

        /**
         * The base for wait.
         */
        double base() default 2;

        /**
         * The multiplier for wait.
         */
        long multiplier() default 0;

        /**
         * The maxDuration for wait.
         */
        long maxDuration() default 0;

        /**
         * Custom wait implement.
         */
        Class<? extends Wait> custom() default Wait.class;
    }
}
