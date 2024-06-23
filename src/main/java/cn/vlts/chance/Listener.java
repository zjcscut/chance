package cn.vlts.chance;

/**
 * Listener which listens to current attempt, will be fired when the state of current attempt changes.
 *
 * @author throwable
 * @since 2024/6/18 14:37
 */
@FunctionalInterface
public interface Listener<V, E extends Throwable> {

    /**
     * Fire when the state of current attempt changes.
     *
     * @param attempt current attempt
     */
    void onFire(Attempt<V, E> attempt);
}
