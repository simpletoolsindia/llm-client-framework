package in.simpletools.llm.framework.model;

import java.util.*;

public class Tool {
    private String type = "function";
    private Function function;

    public static class Function {
        private String name;
        private String description;
        private Map<String, Param> parameters;

        public static class Param {
            private String type = "string";
            private String description;

            public String getType() { return type; }
            public void setType(String t) { this.type = t; }
            public String getDescription() { return description; }
            public void setDescription(String d) { this.description = d; }

            public Map<String, Object> toMap() {
                Map<String, Object> m = new HashMap<>();
                m.put("type", type);
                if (description != null) m.put("description", description);
                return m;
            }
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Param> getParameters() { return parameters; }
        public void setParameters(Map<String, Param> parameters) { this.parameters = parameters; }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("type", "object");
            Map<String, Object> props = new HashMap<>();
            if (parameters != null) parameters.forEach((k, v) -> props.put(k, v.toMap()));
            m.put("properties", props);
            return m;
        }
    }

    public static ToolBuilder builder() { return new ToolBuilder(); }

    public static class ToolBuilder {
        private final Tool tool = new Tool();
        private final Function function = new Function();
        private final Map<String, Function.Param> params = new HashMap<>();

        public ToolBuilder name(String name) { function.setName(name); return this; }
        public ToolBuilder description(String desc) { function.setDescription(desc); return this; }
        public ToolBuilder param(String name, String type, String description) {
            Function.Param p = new Function.Param();
            p.setType(type); p.setDescription(description);
            params.put(name, p); return this;
        }
        public Tool build() { function.setParameters(params); tool.setFunction(function); return tool; }
    }

    public String getType() { return type; }
    public Function getFunction() { return function; }
    public void setFunction(Function function) { this.function = function; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        if (function != null) {
            Map<String, Object> f = new HashMap<>();
            f.put("name", function.getName());
            f.put("description", function.getDescription());
            f.put("parameters", function.toMap());
            m.put("function", f);
        }
        return m;
    }
}