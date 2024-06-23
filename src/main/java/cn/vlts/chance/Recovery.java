package cn.vlts.chance;

/**
 * Recovery for stateful retry after all tries are exhausted while the last failed attempt matches the recoverPredicate.
 * See the required parameter recoverPredicate in {@link Chance.Builder}.
 *
 * @author throwable
 * @since 2024/6/18 23:44
 */
@FunctionalInterface
public interface Recovery<V> {

    /**
     * Recover from stateful retry after all tries are exhausted.
     *
     * @param attempt last attempt that matches the recoverPredicate
     * @return the result that can be used to replace the calling result that failed
     */
    V recover(Attempt<V, ?> attempt);
}
