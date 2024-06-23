package cn.vlts.chance;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * A blocker is used to block {@link Chance#call} between retry attempts.
 *
 * @author throwable
 * @since 2024/6/18 9:33
 */
@FunctionalInterface
public interface Blocker<V, E extends Throwable> {

    /**
     * Try to block {@link Chance#call} between retry attempts with specified blocking time in millis. The block time was
     * computed by {@link Wait#computeWaitDuration}.
     *
     * @param blockMillis block time in millis
     * @param attempt     last attempt
     * @throws InterruptedException if the current thread was interrupted
     */
    void block(long blockMillis, Attempt<V, E> attempt) throws InterruptedException;

    /**
     * Create a ThreadSleepBlocker instance, static factory method.
     *
     * @return a ThreadSleepBlocker instance
     */
    static <V, E extends Throwable> Blocker<V, E> newThreadSleepBlocker() {
        return new ThreadSleepBlocker<>();
    }

    /**
     * Create a LockSupportParkBlocker instance, static factory method.
     *
     * @return a LockSupportParkBlocker instance
     */
    static <V, E extends Throwable> Blocker<V, E> newLockSupportParkBlocker() {
        return new LockSupportParkBlocker<>();
    }

    /**
     * Create an ObjectWaitBlocker instance, static factory method.
     *
     * @return an ObjectWaitBlocker instance
     */
    static <V, E extends Throwable> Blocker<V, E> newObjectWaitBlocker() {
        return new ObjectWaitBlocker<>();
    }

    /**
     * The implement of blocker which used {@link Thread#sleep} to block.
     */
    class ThreadSleepBlocker<V, E extends Throwable> implements Blocker<V, E> {

        private ThreadSleepBlocker() {
        }

        @Override
        public void block(long blockMillis, Attempt<V, E> attempt) throws InterruptedException {
            Thread.sleep(blockMillis);
        }
    }

    /**
     * The implement of blocker which used {@link LockSupport#parkNanos} to block.
     */
    class LockSupportParkBlocker<V, E extends Throwable> implements Blocker<V, E> {

        private LockSupportParkBlocker() {
        }

        @Override
        public void block(long blockMillis, Attempt<V, E> attempt) throws InterruptedException {
            long blockNanos = TimeUnit.MILLISECONDS.toNanos(blockMillis);
            LockSupport.parkNanos(blockNanos);
        }
    }

    /**
     * The implement of blocker which used {@link Object#wait} to block.
     */
    class ObjectWaitBlocker<V, E extends Throwable> implements Blocker<V, E> {

        private final Object mutex = new Object();

        private ObjectWaitBlocker() {
        }

        @Override
        public void block(long blockMillis, Attempt<V, E> attempt) throws InterruptedException {
            synchronized (this.mutex){
                this.mutex.wait(blockMillis);
            }
        }
    }
}
