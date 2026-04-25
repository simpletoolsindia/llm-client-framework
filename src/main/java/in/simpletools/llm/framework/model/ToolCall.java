package in.simpletools.llm.framework.model;

import java.util.HashMap;
import java.util.Map;
import com.google.gson.Gson;

/**
 * Immutable tool call representation.
 */
public record ToolCall(String id, Function function) {

    public record Function(String name, Map<String, Object> arguments) {
        public Function {
            if (arguments == null) arguments = Map.of();
        }

        public String getArgumentsJson() {
            return new Gson().toJson(arguments);
        }

        public <T> T getArgument(String name, Class<T> type) {
            Object val = arguments.get(name);
            if (val == null) return null;
            if (type.isInstance(val)) return type.cast(val);
            return new Gson().fromJson(new Gson().toJson(val), type);
        }

        public Map<String, Object> toMap() {
            return Map.of("name", name, "arguments", arguments);
        }
    }

    public ToolCall {
        if (function == null) function = new Function("", Map.of());
    }

    public ToolCall withId(String id) {
        return new ToolCall(id, function);
    }

    public ToolCall withFunction(Function fn) {
        return new ToolCall(id, fn);
    }

    public Map<String, Object> toMap() {
        return Map.of("id", id != null ? id : "", "type", "function", "function", function.toMap());
    }

    @SuppressWarnings("unchecked")
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
