package in.simpletools.llm.framework.utils;

import java.util.function.Supplier;

/**
 * Functional retry combinator - applies a supplier with exponential backoff.
 */
public final class Retry {

    private Retry() {}

    public record RetryConfig(int maxAttempts, long initialDelayMs, double backoffMultiplier, long maxDelayMs) {
        public static RetryConfig defaults() { return new RetryConfig(3, 500, 2.0, 10_000); }
        public static RetryConfig none() { return new RetryConfig(0, 0, 1.0, 0); }
    }

    /**
     * Retry a supplier with exponential backoff.
     * @param action the action to execute
     * @param config retry configuration
     * @return result of successful execution
     * @throws Exception if all retries exhausted
     */
    public static <T> T withRetry(Supplier<T> action, RetryConfig config) throws Exception {
        if (config.maxAttempts() <= 0) return action.get();

        long delay = config.initialDelayMs();
        Exception lastException = null;

        for (int attempt = 0; attempt < config.maxAttempts(); attempt++) {
            try {
                return action.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt >= config.maxAttempts() - 1) break;
                try { Thread.sleep(delay); }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new Exception("Execution interrupted: " + e.getMessage(), e);
                }
                delay = Math.min((long)(delay * config.backoffMultiplier()), config.maxDelayMs());
            }
        }
        throw lastException != null ? lastException : new Exception("Execution failed after " + config.maxAttempts() + " attempts");
    }

    /**
     * Retry a supplier with default configuration.
     */
    public static <T> T withDefaults(Supplier<T> action) throws Exception {
        return withRetry(action, RetryConfig.defaults());
    }
}
