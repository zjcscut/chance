package cn.vlts.chance;

/**
 * Thrown to indicate that a chance failed after all tries are exhausted or current thread was interrupted.
 *
 * @author throwable
 * @since 2024/6/18 19:49
 */
public class ChanceException extends Exception {

    private final Attempt<?, ?> attempt;

    private final boolean interrupted;

    /**
     * ChanceException
     *
     * @param attempt     the last failed attempt
     * @param interrupted whether current thread was interrupted
     */
    public ChanceException(Attempt<?, ?> attempt, boolean interrupted) {
        super(toMessage(attempt, interrupted));
        this.attempt = attempt;
        this.interrupted = interrupted;
    }

    private static String toMessage(Attempt<?, ?> attempt, boolean interrupted) {
        if (interrupted) {
            return "Retrying calling interrupted after " + attempt.attemptTimes() + " attempts.";
        }
        return "Retrying calling failed after " + attempt.attemptTimes() + " attempts. Exception message: "
                + attempt.exceptionNow().getMessage() + ".";
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public Attempt<?, ?> getAttempt() {
        return attempt;
    }

    @Override
    public Throwable getCause() {
        return attempt.hasException() ? attempt.rootCauseNow() : super.getCause();
    }

    /**
     * Get the exception instance of current failed attempt.
     *
     * @return the exception instance
     */
    public Throwable getException() {
        return attempt.hasException() ? attempt.exceptionNow() : this;
    }
}
