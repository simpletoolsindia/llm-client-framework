package in.simpletools.llm.framework.tool;

import java.util.*;
import java.lang.reflect.*;
import java.util.function.Function;

/**
 * Central registry for LLM tools with auto-registration and retry support.
 *
 * <p>Auto-register tools using the {@link LLMTool} annotation on any object:
 * <pre>
 * {@code
 * MyTools tools = new MyTools();
 * registry.registerAll(tools);  // finds all @LLMTool methods
 * }
 * </pre>
 *
 * <p>Or register manually with a lambda:
 * <pre>
 * {@code
 * registry.register("calc", "Evaluate math", args -> eval(args.get("expr")));
 * }
 * </pre>
 */
public class ToolRegistry {
    private final Map<String, ToolInfo> tools = new HashMap<>();

    /** Extended tool info with retry configuration. */
    public static class ToolInfo {
        private final String name;
        private final String description;
        private final Map<String, ParamInfo> params;
        private final Function<Map<String, Object>, Object> handler;
        private final Method method;
        private final Object instance;
        private final int maxRetries;
        private final long retryDelayMs;
        private final double backoffMultiplier;
        private final long maxRetryDelayMs;

        public ToolInfo(String name, String description, Map<String, ParamInfo> params,
                        Function<Map<String, Object>, Object> handler,
                        Method method, Object instance,
                        int maxRetries, long retryDelayMs,
                        double backoffMultiplier, long maxRetryDelayMs) {
            this.name = name;
            this.description = description;
            this.params = params;
            this.handler = handler;
            this.method = method;
            this.instance = instance;
            this.maxRetries = maxRetries;
            this.retryDelayMs = retryDelayMs;
            this.backoffMultiplier = backoffMultiplier;
            this.maxRetryDelayMs = maxRetryDelayMs;
        }

        /** Constructor for lambda-based tools with default retry. */
        public ToolInfo(String name, String description, Map<String, ParamInfo> params,
                        Function<Map<String, Object>, Object> handler) {
            this(name, description, params, handler, null, null, 3, 500, 2.0, 10000);
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Map<String, ParamInfo> getParams() { return params; }
        public int getMaxRetries() { return maxRetries; }

        /**
         * Invoke the tool with retry on failure.
         * Uses exponential backoff between retries.
         */
        public Object invoke(Map<String, Object> args) throws Exception {
            if (handler != null) {
                return invokeWithRetry(() -> handler.apply(args));
            }

            // Reflective invocation
            Parameter[] paramDefs = method.getParameters();
            Object[] ps = new Object[paramDefs.length];
            for (int i = 0; i < paramDefs.length; i++) {
                String pName = paramDefs[i].getName();
                ToolParam paramAnn = paramDefs[i].getAnnotation(ToolParam.class);
                if (paramAnn != null && !paramAnn.name().isEmpty()) pName = paramAnn.name();
                ps[i] = args.get(pName);
            }
            return invokeWithRetry(() -> method.invoke(instance, ps));
        }

        private Object invokeWithRetry(java.util.function.Supplier<Object> action) throws Exception {
            if (maxRetries <= 0) return action.get();

            int attempt = 0;
            long delay = retryDelayMs;
            Exception lastException = null;

            while (attempt < maxRetries) {
                try {
                    return action.get();
                } catch (Exception e) {
                    lastException = e;
                    attempt++;
                    if (attempt >= maxRetries) break;

                    try { Thread.sleep(delay); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new Exception("Tool execution interrupted: " + e.getMessage(), e);
                    }
                    delay = Math.min((long)(delay * backoffMultiplier), maxRetryDelayMs);
                }
            }
            throw lastException != null ? lastException : new Exception("Tool execution failed");
        }
    }

    public static class ParamInfo {
        private final String name;
        private final String description;
        private final boolean required;
        private final Class<?> type;

        public ParamInfo(String name, String description, boolean required, Class<?> type) {
            this.name = name; this.description = description; this.required = required; this.type = type;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isRequired() { return required; }
        public Class<?> getType() { return type; }

        /** Infer JSON Schema type from Java class. */
        public static String jsonType(Class<?> t) {
            String cn = t.getName();
            if (cn.equals("java.lang.String") || cn.equals("java.lang.Character")) return "string";
            if (cn.equals("int") || cn.equals("java.lang.Integer") || cn.equals("long") || cn.equals("java.lang.Long")
                    || cn.equals("short") || cn.equals("java.lang.Short")) return "integer";
            if (cn.equals("boolean") || cn.equals("java.lang.Boolean")) return "boolean";
            if (cn.equals("double") || cn.equals("java.lang.Double") || cn.equals("float") || cn.equals("java.lang.Float")) return "number";
            return "string";
        }
    }

    /**
     * Auto-register all methods annotated with {@link LLMTool} from a service object.
     * Supports both {@link LLMTool} (new) and legacy {@link OllamaTool}.
     */
    public void registerAll(Object service) {
        for (Method m : service.getClass().getDeclaredMethods()) {
            LLMTool toolAnn = m.getAnnotation(LLMTool.class);
            OllamaTool legacyAnn = m.getAnnotation(OllamaTool.class);

            if (toolAnn != null) {
                registerFromAnnotation(service, m, toolAnn);
            } else if (legacyAnn != null) {
                registerFromLegacyAnnotation(service, m, legacyAnn);
            }
        }
    }

    private void registerFromAnnotation(Object service, Method m, LLMTool ann) {
        String name = ann.name().isEmpty() ? m.getName() : ann.name();
        String desc = ann.description().isEmpty() ? m.getName() : ann.description();

        Map<String, ParamInfo> params = new LinkedHashMap<>();
        for (Parameter p : m.getParameters()) {
            ToolParam paramAnn = p.getAnnotation(ToolParam.class);
            String pName = (paramAnn != null && !paramAnn.name().isEmpty()) ? paramAnn.name() : p.getName();
            String pDesc = (paramAnn != null && !paramAnn.description().isEmpty()) ? paramAnn.description() : "";
            boolean pReq = (paramAnn == null) || paramAnn.required();
            params.put(pName, new ParamInfo(pName, pDesc, pReq, p.getType()));
        }

        tools.put(name, new ToolInfo(
            name, desc, params, null, m, service,
            ann.maxRetries(), ann.retryDelayMs(), ann.backoffMultiplier(), ann.maxRetryDelayMs()
        ));
    }

    private void registerFromLegacyAnnotation(Object service, Method m, OllamaTool ann) {
        String name = ann.name().isEmpty() ? m.getName() : ann.name();
        String desc = ann.description().isEmpty() ? m.getName() : ann.description();

        Map<String, ParamInfo> params = new LinkedHashMap<>();
        for (Parameter p : m.getParameters()) {
            ToolParam paramAnn = p.getAnnotation(ToolParam.class);
            String pName = (paramAnn != null && !paramAnn.name().isEmpty()) ? paramAnn.name() : p.getName();
            String pDesc = (paramAnn != null) ? paramAnn.description() : "";
            params.put(pName, new ParamInfo(pName, pDesc, paramAnn == null || paramAnn.required(), p.getType()));
        }

        tools.put(name, new ToolInfo(name, desc, params, null, m, service, 3, 500, 2.0, 10000));
    }

    /**
     * Register a tool using a lambda/Function.
     * Retry uses defaults (3 attempts, 500ms initial delay, 2x backoff).
     */
    public void register(String name, String description,
                         Function<Map<String, Object>, Object> handler,
                         Map<String, ParamInfo> params) {
        tools.put(name, new ToolInfo(name, description, params, handler));
    }

    /**
     * Register a tool with full retry configuration.
     */
    public void register(String name, String description,
                         Function<Map<String, Object>, Object> handler,
                         Map<String, ParamInfo> params,
                         int maxRetries, long retryDelayMs,
                         double backoffMultiplier, long maxRetryDelayMs) {
        tools.put(name, new ToolInfo(name, description, params, handler, null, null,
            maxRetries, retryDelayMs, backoffMultiplier, maxRetryDelayMs));
    }

    /** Register a simple tool with no parameters. */
    public void register(String name, String description, Runnable runnable) {
        tools.put(name, new ToolInfo(name, description, Map.of(), args -> {
            runnable.run(); return "Done";
        }));
    }

    public ToolInfo get(String name) { return tools.get(name); }
    public Set<String> getToolNames() { return tools.keySet(); }
    public Collection<ToolInfo> getAllTools() { return tools.values(); }
    public int getToolCount() { return tools.size(); }
    public void clear() { tools.clear(); }
}
