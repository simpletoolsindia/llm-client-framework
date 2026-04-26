package in.simpletools.llm.framework.tool;

import in.simpletools.llm.framework.utils.Retry;
import java.util.*;
import java.lang.reflect.*;
import java.util.function.Function;

/**
 * Central registry for LLM-callable tools.
 *
 * <p>The registry stores tool metadata used to build provider tool schemas and
 * the Java handlers invoked when the model requests a tool call. Most
 * applications interact with this indirectly through
 * {@link in.simpletools.llm.framework.client.LLMClient#tool(String, String, Function)}
 * or {@link in.simpletools.llm.framework.client.LLMClient#registerTools(Object)}.</p>
 *
 * <p>Tools can be registered in two ways:</p>
 * <ul>
 *   <li>Lambda registration with explicit parameter metadata</li>
 *   <li>Annotation registration from {@link LLMTool}-annotated methods</li>
 * </ul>
 */
public class ToolRegistry {
    private final Map<String, ToolInfo> tools = new HashMap<>();

    /**
     * Runtime metadata and invocation handle for one registered tool.
     *
     * @param name tool name exposed to the model
     * @param description tool description exposed to the model
     * @param params parameter metadata keyed by parameter name
     * @param handler lambda handler, when registered programmatically
     * @param method reflective method, when registered from annotations
     * @param instance service instance for reflective invocation
     * @param retryConfig retry behavior for invocation failures
     */
    public record ToolInfo(
        String name,
        String description,
        Map<String, ParamInfo> params,
        Function<Map<String, Object>, Object> handler,
        Method method,
        Object instance,
        Retry.RetryConfig retryConfig
    ) {
        /**
         * Invoke this tool using model-provided arguments.
         *
         * @param args decoded argument map
         * @return tool result object
         * @throws Exception when invocation fails after retries
         */
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

    /**
     * Tool parameter metadata.
     *
     * @param name parameter name exposed to the model
     * @param description human-readable parameter description
     * @param required whether the model should provide this parameter
     * @param type Java type used to infer the provider JSON type
     */
    public record ParamInfo(String name, String description, boolean required, Class<?> type) {
        /**
         * Convert a Java type to the JSON schema primitive type used in tool schemas.
         *
         * @param t Java type
         * @return JSON schema type name
         */
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

    /**
     * Register a tool using a lambda/function handler.
     *
     * @param name tool name exposed to the model
     * @param description tool description exposed to the model
     * @param handler handler invoked with decoded arguments
     * @param params parameter metadata
     */
    public void register(String name, String description,
                         Function<Map<String, Object>, Object> handler,
                         Map<String, ParamInfo> params) {
        tools.put(name, new ToolInfo(name, description, Map.copyOf(params), handler,
            null, null, Retry.RetryConfig.defaults()));
    }

    /**
     * Register a tool with explicit retry configuration.
     *
     * @param name tool name exposed to the model
     * @param description tool description exposed to the model
     * @param handler handler invoked with decoded arguments
     * @param params parameter metadata
     * @param maxRetries maximum attempts before surfacing an error
     * @param retryDelayMs initial retry delay in milliseconds
     * @param backoffMultiplier exponential backoff multiplier
     * @param maxRetryDelayMs maximum retry delay in milliseconds
     */
    public void register(String name, String description,
                         Function<Map<String, Object>, Object> handler,
                         Map<String, ParamInfo> params,
                         int maxRetries, long retryDelayMs,
                         double backoffMultiplier, long maxRetryDelayMs) {
        var retryConfig = new Retry.RetryConfig(maxRetries, retryDelayMs, backoffMultiplier, maxRetryDelayMs);
        tools.put(name, new ToolInfo(name, description, Map.copyOf(params), handler,
            null, null, retryConfig));
    }

    /**
     * Register a no-argument command tool.
     *
     * @param name tool name exposed to the model
     * @param description tool description exposed to the model
     * @param runnable command to execute
     */
    public void register(String name, String description, Runnable runnable) {
        tools.put(name, new ToolInfo(name, description, Map.of(), args -> {
            runnable.run(); return "Done";
        }, null, null, Retry.RetryConfig.defaults()));
    }

    /** @param name tool name @return matching tool metadata or null */
    public ToolInfo get(String name) { return tools.get(name); }
    /** @return registered tool names */
    public Set<String> getToolNames() { return tools.keySet(); }
    /** @return registered tool metadata */
    public Collection<ToolInfo> getAllTools() { return tools.values(); }
    /** @return number of registered tools */
    public int getToolCount() { return tools.size(); }
    /** Remove all registered tools. */
    public void clear() { tools.clear(); }
}
