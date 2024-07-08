package cn.vlts.chance;

/**
 * The chance option.
 *
 * @author throwable
 * @since 2024/7/6 17:24
 */
@FunctionalInterface
public interface Opt {

    /**
     * Check whether current option is supported.
     *
     * @param opts the options value.
     * @return supported or not
     */
    default boolean support(int opts) {
        return (opts & value()) != 0;
    }

    /**
     * Option value.
     */
    int value();

    /**
     * Chance internal options. Internal option value is between [1 << 0, 1 << 15], while custom option value must be
     * between [1 << 16, 1 << 31].
     */
    enum InternalOpt implements Opt {

        /**
         * Recording current system time.
         */
        RECORDING_SYSTEM_TIME(1),

        /**
         * ForeverChoice.
         */
        FOREVER_CHOICE(1 << 1),

        /**
         * Listeners.
         */
        LISTENERS(1 << 2),

        /**
         * Cancelling chance.
         */
        CANCELLING_CHANCE(1 << 3),

        /**
         * Exception type equality comparison.
         */
        EXCEPTION_TYPE_EQUALITY_COMPARISON(1 << 4),

        /**
         * Recovery.
         */
        RECOVERY(1 << 5),

        ;

        private final int opt;

        InternalOpt(int opt) {
            this.opt = opt;
        }

        @Override
        public int value() {
            return this.opt;
        }

        public static int getAllInternalOpts() {
            int opts = 0;
            for (InternalOpt opt : values()) {
                opts |= opt.value();
            }
            return opts;
        }
    }
}
