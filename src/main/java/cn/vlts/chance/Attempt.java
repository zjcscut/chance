package cn.vlts.chance;

import java.util.Objects;

/**
 * An attempt of a call. A call completed successfully with a result or encounter any exception will be stored
 * by attempt, the state, context metadata of the call will be managed at the same time.
 *
 * @author throwable
 * @since 2024/6/17 11:58
 */
public interface Attempt<V, E extends Throwable> {

    /**
     * Return the state of current attempt.
     *
     * @return the state of current attempt
     */
    State state();

    /**
     * Cancel current attempt.
     *
     * @return true if current attempt has been canceled, otherwise false
     */
    boolean cancel();

    /**
     * Force cancelling current attempt.
     */
    void forceCancel();

    /**
     * Return the result of current attempt.
     *
     * @return the result of current attempt, if state is not {@link State#SUCCESS}, the method will throw an
     * IllegalStateException.
     */
    V resultNow();

    /**
     * Return the exception from current attempt.
     *
     * @return the exception from current attempt, if state is not {@link State#FAILED}, the method will throw an
     * IllegalStateException
     */
    E exceptionNow();

    /**
     * Return the root cause of exception from current attempt.
     *
     * @return the root cause
     */
    <S extends E> S rootCauseNow();

    /**
     * Return the attempt times of current attempt, the initial attempt times(means the first attempt) is 1.
     *
     * @return the attempt times of current attempt
     */
    int attemptTimes();

    /**
     * Return the initial time in nanos, which is the system time when {@link Chance} is called.
     *
     * @return the initial time in nanos
     */
    long initialNanos();

    /**
     * Return the start time in nanos, which is the system time when current attempt is created.
     *
     * @return the start time in nanos
     */
    long startNanos();

    /**
     * Return the completed time in nanos, which is the system time when current attempt is completed.
     *
     * @return the completed time in nanos
     */
    long completionNanos();

    /**
     * Check whether result of current attempt is existed or not, {@link State#SUCCESS} means result must be existed.
     *
     * @return the result is existed or not
     */
    default boolean hasResult() {
        return Objects.equals(state(), State.SUCCESS);
    }

    /**
     * Check whether exception from current attempt is existed or not, {@link State#FAILED} means exception must be existed.
     *
     * @return the exception is existed or not
     */
    default boolean hasException() {
        return Objects.equals(state(), State.FAILED);
    }

    /**
     * Check whether current attempt is canceled or not, {@link State#CANCELLED} means current attempt must be canceled.
     *
     * @return canceled or not
     */
    default boolean isCancelled() {
        return Objects.equals(state(), State.CANCELLED);
    }

    /**
     * The state of attempt.
     */
    enum State {

        /**
         * The attempt has not completed.
         */
        RUNNING,

        /**
         * The attempt completed with a result.
         */
        SUCCESS,

        /**
         * The attempt completed with an exception.
         */
        FAILED,

        /**
         * The attempt has been canceled.
         */
        CANCELLED
    }
}
