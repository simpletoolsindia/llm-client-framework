package in.simpletools.llm.framework.tool;

import in.simpletools.llm.framework.utils.Retry;
import java.util.*;
import java.lang.reflect.*;
import java.util.function.Function;

/**
 * Central registry for LLM tools with auto-registration and retry support.
 */
public class ToolRegistry {
    private final Map<String, ToolInfo> tools = new HashMap<>();

    public record ToolInfo(
        String name,
        String description,
        Map<String, ParamInfo> params,
        Function<Map<String, Object>, Object> handler,
        Method method,
        Object instance,
        Retry.RetryConfig retryConfig
    ) {
        public Object invoke(Map<String, Object> args) throws Exception {
            if (handler != null) {
                return Retry.withRetry(() -> handler.apply(args), retryConfig);
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
            return Retry.withRetry(() -> {
                try { return method.invoke(instance, ps); }
                catch (IllegalAccessException e) { throw new RuntimeException(e); }
                catch (InvocationTargetException e) {
                    throw e.getCause() != null ? new RuntimeException(e.getCause()) : new RuntimeException(e);
                }
            }, retryConfig);
        }
    }

    public record ParamInfo(String name, String description, boolean required, Class<?> type) {
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

        var retryConfig = new Retry.RetryConfig(
            ann.maxRetries(), ann.retryDelayMs(), ann.backoffMultiplier(), ann.maxRetryDelayMs());

        tools.put(name, new ToolInfo(name, desc, Map.copyOf(params), null, m, service, retryConfig));
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

        tools.put(name, new ToolInfo(name, desc, Map.copyOf(params), null, m, service, Retry.RetryConfig.defaults()));
    }

    /** Register a tool using a lambda/Function. */
    public void register(String name, String description,
                         Function<Map<String, Object>, Object> handler,
                         Map<String, ParamInfo> params) {
        tools.put(name, new ToolInfo(name, description, Map.copyOf(params), handler,
            null, null, Retry.RetryConfig.defaults()));
    }

    /** Register a tool with full retry configuration. */
    public void register(String name, String description,
                         Function<Map<String, Object>, Object> handler,
                         Map<String, ParamInfo> params,
                         int maxRetries, long retryDelayMs,
                         double backoffMultiplier, long maxRetryDelayMs) {
        var retryConfig = new Retry.RetryConfig(maxRetries, retryDelayMs, backoffMultiplier, maxRetryDelayMs);
        tools.put(name, new ToolInfo(name, description, Map.copyOf(params), handler,
            null, null, retryConfig));
    }

    /** Register a simple tool with no parameters. */
    public void register(String name, String description, Runnable runnable) {
        tools.put(name, new ToolInfo(name, description, Map.of(), args -> {
            runnable.run(); return "Done";
        }, null, null, Retry.RetryConfig.defaults()));
    }

    public ToolInfo get(String name) { return tools.get(name); }
    public Set<String> getToolNames() { return tools.keySet(); }
    public Collection<ToolInfo> getAllTools() { return tools.values(); }
    public int getToolCount() { return tools.size(); }
    public void clear() { tools.clear(); }
}
