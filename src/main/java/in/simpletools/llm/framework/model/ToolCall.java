package in.simpletools.llm.framework.model;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;

/**
 * Immutable tool call requested by a model.
 *
 * <p>When an assistant response includes tool calls, the client looks up each
 * tool name in {@link in.simpletools.llm.framework.tool.ToolRegistry}, invokes
 * the Java handler with {@link Function#arguments()}, appends tool-result
 * messages, and continues the conversation.</p>
 *
 * @param id provider tool-call id, when supplied
 * @param function function name and argument map
 */
public record ToolCall(String id, Function function) {

    /**
     * Tool function invocation payload.
     *
     * @param name tool/function name
     * @param arguments decoded argument map supplied by the model
     */
    public record Function(String name, Map<String, Object> arguments) {
        public Function {
            if (arguments == null) arguments = Map.of();
        }

        /** @return arguments serialized as JSON */
        public String getArgumentsJson() {
            return new Gson().toJson(arguments);
        }

        /**
         * Read and convert one argument.
         *
         * @param name argument name
         * @param type desired Java type
         * @return converted argument value, or null when missing
         * @param <T> target type
         */
        public <T> T getArgument(String name, Class<T> type) {
            Object val = arguments.get(name);
            if (val == null) return null;
            if (type.isInstance(val)) return type.cast(val);
            return new Gson().fromJson(new Gson().toJson(val), type);
        }

        /** @return provider-style function map */
        public Map<String, Object> toMap() {
            return Map.of("name", name, "arguments", arguments);
        }
    }

    public ToolCall {
        if (function == null) function = new Function("", Map.of());
    }

    /** @param id replacement provider id @return copy with the supplied id */
    public ToolCall withId(String id) {
        return new ToolCall(id, function);
    }

    /** @param fn replacement function payload @return copy with the supplied function */
    public ToolCall withFunction(Function fn) {
        return new ToolCall(id, fn);
    }

    /** @return provider-style tool-call map */
    public Map<String, Object> toMap() {
        return Map.of("id", id != null ? id : "", "type", "function", "function", function.toMap());
    }

    @SuppressWarnings("unchecked")
    /**
     * Parse a tool call from a provider-style map.
     *
     * @param m map containing function name and arguments
     * @return parsed tool call
     */
    public static ToolCall fromMap(Map<String, Object> m) {
        var id = (String) m.get("id");
        var f = m.get("function");
        Function fn = new Function("", Map.of());
        if (f instanceof Map<?, ?> fm) {
            var name = (String) fm.get("name");
            var args = fm.get("arguments");
            Map<String, Object> arguments = args instanceof Map ? (Map<String, Object>) args
                : args instanceof String ? new Gson().fromJson((String) args, Map.class)
                : Map.of();
            fn = new Function(name != null ? name : "", arguments);
        }
        return new ToolCall(id, fn);
    }
}
