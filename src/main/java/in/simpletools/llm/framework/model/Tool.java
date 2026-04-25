package in.simpletools.llm.framework.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable tool definition for LLM function calling.
 */
public record Tool(String type, Function function) {

    public record Function(String name, String description, Map<String, Param> parameters) {
        public record Param(String type, String description) {
            public Param {
                if (type == null) type = "string";
            }

            public Map<String, Object> toMap() {
                var m = new HashMap<String, Object>();
                m.put("type", type);
                if (description != null) m.put("description", description);
                return m;
            }
        }

        public Function {
            if (parameters == null) parameters = Map.of();
        }

        public Map<String, Object> toMap() {
            var props = parameters.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toMap()));
            return Map.of("type", "object", "properties", props);
        }
    }

    public Tool {
        if (type == null) type = "function";
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String name;
        private String description;
        private final Map<String, Function.Param> params = new HashMap<>();

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String desc) { this.description = desc; return this; }
        public Builder param(String name, String type, String description) {
            params.put(name, new Function.Param(type, description)); return this;
        }
        public Tool build() {
            var fn = new Function(name, description, Map.copyOf(params));
            return new Tool("function", fn);
        }
    }

    public Map<String, Object> toMap() {
        return Map.of("type", type, "function", function.toMap());
    }
}
