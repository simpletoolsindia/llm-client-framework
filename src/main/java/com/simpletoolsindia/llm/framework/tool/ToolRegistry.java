package com.simpletoolsindia.llm.framework.tool;

import java.util.*;
import java.lang.reflect.*;
import java.util.function.Function;

public class ToolRegistry {
    private final Map<String, ToolInfo> tools = new HashMap<>();

    public static class ToolInfo {
        private final String name;
        private final String description;
        private final Map<String, ParamInfo> params;
        private final Function<Map<String, Object>, Object> handler;
        private final Method method;
        private final Object instance;

        public ToolInfo(String name, String description,
                       Map<String, ParamInfo> params,
                       Function<Map<String, Object>, Object> handler,
                       Method method, Object instance) {
            this.name = name; this.description = description;
            this.params = params; this.handler = handler;
            this.method = method; this.instance = instance;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Map<String, ParamInfo> getParams() { return params; }

        public Object invoke(Map<String, Object> args) throws Exception {
            if (handler != null) return handler.apply(args);
            Object[] ps = new Object[method.getParameterCount()];
            Parameter[] paramDefs = method.getParameters();
            for (int i = 0; i < paramDefs.length; i++) {
                String pName = paramDefs[i].getName();
                ToolParam ann = paramDefs[i].getAnnotation(ToolParam.class);
                if (ann != null && !ann.name().isEmpty()) pName = ann.name();
                ps[i] = convert(args.get(pName), paramDefs[i].getType());
            }
            return method.invoke(instance, ps);
        }

        private Object convert(Object value, Class<?> targetType) {
            if (value == null) return null;
            if (targetType.isInstance(value)) return value;
            if (value instanceof Number) {
                Number n = (Number) value;
                if (targetType == int.class || targetType == Integer.class) return n.intValue();
                if (targetType == long.class || targetType == Long.class) return n.longValue();
                if (targetType == double.class || targetType == Double.class) return n.doubleValue();
            }
            if (targetType == String.class) return String.valueOf(value);
            return value;
        }
    }

    public static class ParamInfo {
        private final String name;
        private final String description;
        private final boolean required;
        private final Class<?> type;

        public ParamInfo(String name, String description, boolean required, Class<?> type) {
            this.name = name; this.description = description;
            this.required = required; this.type = type;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isRequired() { return required; }
        public Class<?> getType() { return type; }

        public com.simpletoolsindia.llm.framework.model.Tool.Function.Param toMap() {
            com.simpletoolsindia.llm.framework.model.Tool.Function.Param p =
                new com.simpletoolsindia.llm.framework.model.Tool.Function.Param();
            p.setType(getTypeName(type));
            p.setDescription(description);
            return p;
        }

        private String getTypeName(Class<?> c) {
            if (c == String.class) return "string";
            if (c == int.class || c == Integer.class || c == long.class || c == Long.class) return "integer";
            if (c == double.class || c == Double.class || c == float.class || c == Float.class) return "number";
            if (c == boolean.class || c == Boolean.class) return "boolean";
            return "string";
        }
    }

    public void register(Object service) {
        for (Method m : service.getClass().getDeclaredMethods()) {
            OllamaTool toolAnn = m.getAnnotation(OllamaTool.class);
            if (toolAnn == null) continue;

            String name = toolAnn.name().isEmpty() ? m.getName() : toolAnn.name();
            String desc = toolAnn.description().isEmpty() ? m.getName() : toolAnn.description();

            Map<String, ParamInfo> params = new LinkedHashMap<>();
            for (Parameter p : m.getParameters()) {
                ToolParam paramAnn = p.getAnnotation(ToolParam.class);
                String pName = paramAnn != null && !paramAnn.name().isEmpty()
                    ? paramAnn.name() : p.getName();
                String pDesc = paramAnn != null ? paramAnn.description() : "";
                boolean required = paramAnn == null || paramAnn.required();
                params.put(pName, new ParamInfo(pName, pDesc, required, p.getType()));
            }

            tools.put(name, new ToolInfo(name, desc, params, null, m, service));
        }
    }

    public void register(String name, String description,
                        Function<Map<String, Object>, Object> handler,
                        Map<String, ParamInfo> params) {
        tools.put(name, new ToolInfo(name, description, params, handler, null, null));
    }

    public ToolInfo get(String name) { return tools.get(name); }
    public Set<String> getToolNames() { return tools.keySet(); }
    public Collection<ToolInfo> getAllTools() { return tools.values(); }
    public int getToolCount() { return tools.size(); }
    public void clear() { tools.clear(); }
}