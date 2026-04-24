package in.simpletools.llm.framework.model;

import java.util.*;
import com.google.gson.Gson;

public class ToolCall {
    private String id;
    private Function function;

    public static class Function {
        private String name;
        private Map<String, Object> arguments;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Map<String, Object> getArguments() { return arguments; }
        public void setArguments(Map<String, Object> arguments) { this.arguments = arguments; }

        public String getArgumentsJson() {
            return new Gson().toJson(arguments);
        }

        public <T> T getArgument(String name, Class<T> type) {
            Object val = arguments != null ? arguments.get(name) : null;
            if (val == null) return null;
            if (type.isInstance(val)) return type.cast(val);
            return new Gson().fromJson(new Gson().toJson(val), type);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("name", name);
            m.put("arguments", arguments != null ? arguments : new HashMap<>());
            return m;
        }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Function getFunction() { return function; }
    public void setFunction(Function function) { this.function = function; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("type", "function");
        m.put("function", function != null ? function.toMap() : null);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static ToolCall fromMap(Map<String, Object> m) {
        ToolCall tc = new ToolCall();
        tc.setId((String) m.get("id"));
        Object f = m.get("function");
        if (f instanceof Map) {
            Function fn = new Function();
            Map<String, Object> fm = (Map<String, Object>) f;
            fn.setName((String) fm.get("name"));
            Object args = fm.get("arguments");
            if (args instanceof Map) fn.setArguments((Map<String, Object>) args);
            else if (args instanceof String) fn.setArguments(new Gson().fromJson((String) args, Map.class));
            tc.setFunction(fn);
        }
        return tc;
    }
}