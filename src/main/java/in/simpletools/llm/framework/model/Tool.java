package in.simpletools.llm.framework.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable tool definition for LLM function calling.
 *
 * <p>A {@code Tool} describes a Java function in the JSON-schema-like shape
 * expected by provider APIs. Most users register tools through
 * {@link in.simpletools.llm.framework.client.LLMClient#tool(String, String, java.util.function.Function)}
 * or {@link in.simpletools.llm.framework.tool.LLMTool}; this record is useful
 * for adapters and advanced manual tool-schema construction.</p>
 *
 * @param type tool type, normally {@code function}
 * @param function function name, description, and parameters
 */
public record Tool(String type, Function function) {

    /**
     * Function metadata exposed to the model.
     *
     * @param name tool/function name
     * @param description human-readable explanation of when to call the tool
     * @param parameters parameter schema keyed by parameter name
     */
    public record Function(String name, String description, Map<String, Param> parameters) {
        /**
         * Function parameter metadata.
         *
         * @param type JSON type such as {@code string}, {@code integer}, {@code boolean}, or {@code number}
         * @param description human-readable parameter description
         */
        public record Param(String type, String description) {
            public Param {
                if (type == null) type = "string";
            }

            /** @return JSON-schema-like map for this parameter */
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

        /** @return JSON-schema-like function parameter map */
        public Map<String, Object> toMap() {
            var props = parameters.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toMap()));
            return Map.of("type", "object", "properties", props);
        }
    }

    public Tool {
        if (type == null) type = "function";
    }

    /** @return fluent tool builder */
    public static Builder builder() { return new Builder(); }

    /**
     * Fluent builder for function tools.
     */
    public static class Builder {
        private String name;
        private String description;
        private final Map<String, Function.Param> params = new HashMap<>();

        /** @param name tool/function name @return this builder */
        public Builder name(String name) { this.name = name; return this; }
        /** @param desc tool description @return this builder */
        public Builder description(String desc) { this.description = desc; return this; }
        /** @param name parameter name @param type JSON type @param description parameter description @return this builder */
        public Builder param(String name, String type, String description) {
            params.put(name, new Function.Param(type, description)); return this;
        }
        /** @return immutable tool definition */
        public Tool build() {
            var fn = new Function(name, description, Map.copyOf(params));
            return new Tool("function", fn);
        }
    }

    /** @return provider-style tool map */
    public Map<String, Object> toMap() {
        return Map.of("type", type, "function", function.toMap());
    }
}
