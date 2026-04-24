package in.simpletools.llm.framework.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Simple, lightweight logger for the LLM framework.
 * No external dependencies - uses only Java standard library.
 *
 * <pre>
 * {@code
 * SimpleLogger logger = SimpleLogger.get("LLMClient");
 * logger.info("Starting chat...");
 * logger.debug("Request: {}", request);
 * logger.warn("Retrying after failure");
 * logger.error("Failed", exception);
 *
 * // Change log level
 * logger.setLevel(Level.DEBUG);
 *
 * // Disable entirely
 * logger.setLevel(Level.OFF);
 *
 * // Custom output
 * logger.addHandler(output -> System.out.println("[CUSTOM]" + output));
 * }
 * </pre>
 */
public class SimpleLogger {
    public enum Level { DEBUG, INFO, WARN, ERROR, OFF }

    private static final Map<String, SimpleLogger> loggers = new ConcurrentHashMap<>();
    private static Level globalLevel = Level.INFO;
    private static final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final List<Handler> additionalGlobalHandlers = new ArrayList<>();

    public interface Handler { void log(String output); }

    private final String name;
    private Level level;

    private SimpleLogger(String name) {
        this.name = name;
        this.level = globalLevel;
    }

    public static SimpleLogger get(String name) {
        return loggers.computeIfAbsent(name, SimpleLogger::new);
    }

    // ===== Configuration =====

    public void setLevel(Level level) { this.level = level; }
    public Level getLevel() { return level; }
    public static void setGlobalLevel(Level level) { globalLevel = level; }
    public static Level getGlobalLevel() { return globalLevel; }

    public static void addGlobalHandler(Handler h) { additionalGlobalHandlers.add(h); }
    public static void clearGlobalHandlers() { additionalGlobalHandlers.clear(); }

    // ===== Logging Methods =====

    public void debug(String msg) { log(Level.DEBUG, msg, null); }
    public void debug(String msg, Object... args) { log(Level.DEBUG, format(msg, args), null); }
    public void debug(String msg, Throwable t) { log(Level.DEBUG, msg, t); }

    public void info(String msg) { log(Level.INFO, msg, null); }
    public void info(String msg, Object... args) { log(Level.INFO, format(msg, args), null); }
    public void info(String msg, Throwable t) { log(Level.INFO, msg, t); }

    public void warn(String msg) { log(Level.WARN, msg, null); }
    public void warn(String msg, Object... args) { log(Level.WARN, format(msg, args), null); }
    public void warn(String msg, Throwable t) { log(Level.WARN, msg, t); }

    public void error(String msg) { log(Level.ERROR, msg, null); }
    public void error(String msg, Object... args) { log(Level.ERROR, format(msg, args), null); }
    public void error(String msg, Throwable t) { log(Level.ERROR, msg, t); }

    private void log(Level lvl, String msg, Throwable t) {
        if (lvl.ordinal() < this.level.ordinal() || this.level == Level.OFF) return;

        String timestamp = LocalDateTime.now().format(fmt);
        String levelStr = lvl.name();
        StringBuilder sb = new StringBuilder();
        sb.append(timestamp).append(" [").append(levelStr).append("] [").append(name).append("] ").append(msg);
        if (t != null) {
            sb.append("\n  Exception: ").append(t.getClass().getName()).append(": ").append(t.getMessage());
            for (StackTraceElement ste : t.getStackTrace()) {
                if (ste.getClassName().startsWith("in.simpletools")) {
                    sb.append("\n    at ").append(ste.getClassName()).append(".").append(ste.getMethodName())
                      .append("(").append(ste.getFileName()).append(":").append(ste.getLineNumber()).append(")");
                    break;
                }
            }
        }

        String output = sb.toString();
        System.out.println(output);

        // Global handlers
        for (Handler h : additionalGlobalHandlers) h.log(output);
    }

    private static String format(String msg, Object... args) {
        if (args == null || args.length == 0) return msg;
        String result = msg;
        for (Object arg : args) {
            int idx = result.indexOf("{}");
            if (idx >= 0) {
                result = result.substring(0, idx) + nullToString(arg) + result.substring(idx + 2);
            }
        }
        return result;
    }

    private static String nullToString(Object o) {
        if (o == null) return "null";
        if (o.getClass().isArray()) {
            if (o instanceof Object[]) return Arrays.toString((Object[]) o);
            if (o instanceof int[]) return Arrays.toString((int[]) o);
            if (o instanceof long[]) return Arrays.toString((long[]) o);
            if (o instanceof double[]) return Arrays.toString((double[]) o);
        }
        return o.toString();
    }
}
