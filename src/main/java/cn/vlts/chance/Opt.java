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
     * @param opts the option bit array.
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
     * Chance internal options. Internal option value is between [1 << 0, 1 << 15], custom option value must be
     * between [1 << 16, 1 << 31].
     */
    enum InternalOpt implements Opt {

        /**
         * Enable recording current system time.
         */
        ENABLE_RECORDING_SYSTEM_TIME(1),

        /**
         * Enable ForeverChoice.
         */
        ENABLE_FOREVER_CHOICE(1 << 1),

        /**
         * Enable listeners.
         */
        ENABLE_LISTENERS(1 << 2),

        /**
         * Enable cancelling chance.
         */
        ENABLE_CANCELLING_CHANCE(1 << 3),

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
                opts += opt.value();
            }
            return opts;
        }
    }
}
