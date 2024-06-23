package cn.vlts.chance;

import java.lang.reflect.Constructor;
import java.util.Objects;

/**
 * Utilities.
 *
 * @author throwable
 * @since 2024/6/23 01:59
 */
public enum Utils {
    U;

    /**
     * Get root cause of throwable.
     *
     * @param throwable the given throwable
     * @return root cause
     */
    public Throwable getRootCause(Throwable throwable) {
        Objects.requireNonNull(throwable);
        Throwable cause;
        while (Objects.nonNull(cause = throwable.getCause())) {
            throwable = cause;
        }
        return throwable;
    }

    /**
     * Create a new instance with default constructor for given type.
     *
     * @param clazz the given type
     * @return a new instance
     * @throws IllegalArgumentException if the default (no args) constructor is not existed or init failed
     */
    public <T> T newInstance(Class<? extends T> clazz) {
        try {
            Constructor<? extends T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
