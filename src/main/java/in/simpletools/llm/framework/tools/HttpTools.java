package in.simpletools.llm.framework.tools;

import in.simpletools.llm.framework.tool.*;
import in.simpletools.llm.framework.utils.SimpleLogger;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * HTTP client tools for calling external REST APIs.
 * Supports GET, POST, PUT, PATCH, DELETE with custom headers, query params, and body.
 *
 * <pre>
 * {@code
 * // Register with LLMClient
 * LLMClient client = LLMClient.ollama("gemma4");
 * client.withHttpTools();  // adds http_get, http_post, http_put, http_patch, http_delete
 *
 * // Or register individually
 * ToolRegistry reg = new ToolRegistry();
 * HttpTools.registerAll(reg);
 * }
 * </pre>
 *
 * <p><b>Tools provided:</b>
 * <ul>
 *   <li>{@code http_get(url, headers, params)} - Fetch data from an API</li>
 *   <li>{@code http_post(url, headers, body)} - Create a new resource</li>
 *   <li>{@code http_put(url, headers, body)} - Replace a resource entirely</li>
 *   <li>{@code http_patch(url, headers, body)} - Partially update a resource</li>
 *   <li>{@code http_delete(url, headers)} - Delete a resource</li>
 * </ul>
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Configurable timeout (default 30s connect, 60s read)</li>
 *   <li>Custom headers support (Auth, Content-Type, etc.)</li>
 *   <li>JSON body support with automatic content-type</li>
 *   <li>Query parameters for GET/DELETE</li>
 *   <li>Detailed response with status, body, headers</li>
 * </ul>
 */
public class HttpTools {
    private static final SimpleLogger log = SimpleLogger.get("HttpTools");

    // ===== Default Timeouts =====
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;  // 30 seconds
    private static final int DEFAULT_READ_TIMEOUT_MS = 60_000;     // 60 seconds

    // ===== Response format =====
    /** JSON-friendly response wrapper. */
    public record HttpResponse(
        int status,
        String body,
        String contentType,
        long responseTimeMs
    ) {
        public String summary() {
            String preview = body != null && body.length() > 200
                ? body.substring(0, 200) + "..."
                : (body != null ? body : "");
            return String.format(
                "HTTP %d | %dms | %s | %s",
                status, responseTimeMs, contentType, preview
            );
        }
    }

    // ===== HTTP Methods =====

    /**
     * Perform HTTP GET request to fetch data from an API.
     *
     * @param url     Full URL to request (required)
     * @param headers Optional map of HTTP headers (e.g., Authorization, Accept)
     * @param params  Optional query parameters appended to URL
     * @return JSON response with status, body, content-type, and response time
     */
    @LLMTool(name = "http_get", description = "Make an HTTP GET request to fetch data from a URL. Returns status, body, and response time.", maxRetries = 2, retryDelayMs = 1000)
    public String http_get(
            @ToolParam(name = "url", description = "The full URL to fetch (e.g., https://api.example.com/users)") String url,
            @ToolParam(name = "headers", description = "Optional HTTP headers as JSON string (e.g., {\"Authorization\": \"Bearer token\", \"Accept\": \"application/json\"})", required = false) String headers,
            @ToolParam(name = "params", description = "Optional query parameters as JSON string (e.g., {\"page\": \"1\", \"limit\": \"10\"})", required = false) String params
    ) {
        try {
            String fullUrl = buildUrl(url, parseQueryParams(params));
            Map<String, String> hdrs = parseHeaders(headers);
            long start = System.currentTimeMillis();
            HttpURLConnection conn = openConnection(fullUrl, "GET", hdrs);
            int status = conn.getResponseCode();
            String body = readBody(conn);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("GET {} -> {} in {}ms", fullUrl, status, elapsed);
            return toJson(new HttpResponse(status, body, conn.getContentType(), elapsed));
        } catch (Exception e) {
            log.error("HTTP GET failed for {}: {}", url, e.getMessage());
            return errorJson("GET", url, e.getMessage());
        }
    }

    /**
     * Perform HTTP POST request to create a new resource.
     *
     * @param url     Full URL to post to
     * @param headers Optional HTTP headers
     * @param body    Request body (JSON string, will set Content-Type: application/json if not specified)
     * @return JSON response with status, body, and response time
     */
    @LLMTool(name = "http_post", description = "Make an HTTP POST request to create or submit data to a URL. Returns status, body, and response time.", maxRetries = 2, retryDelayMs = 1000)
    public String http_post(
            @ToolParam(name = "url", description = "The full URL to POST to") String url,
            @ToolParam(name = "headers", description = "Optional HTTP headers as JSON string", required = false) String headers,
            @ToolParam(name = "body", description = "Request body as JSON string (will set Content-Type to application/json)", required = false) String body
    ) {
        try {
            Map<String, String> hdrs = parseHeaders(headers);
            setContentTypeIfMissing(hdrs, "application/json");
            long start = System.currentTimeMillis();
            HttpURLConnection conn = openConnection(url, "POST", hdrs);
            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                try (var w = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                    w.write(body);
                }
            }
            int status = conn.getResponseCode();
            String responseBody = readBody(conn);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("POST {} -> {} in {}ms", url, status, elapsed);
            return toJson(new HttpResponse(status, responseBody, conn.getContentType(), elapsed));
        } catch (Exception e) {
            log.error("HTTP POST failed for {}: {}", url, e.getMessage());
            return errorJson("POST", url, e.getMessage());
        }
    }

    /**
     * Perform HTTP PUT request to replace a resource entirely.
     *
     * @param url     Full URL to put to
     * @param headers Optional HTTP headers
     * @param body    Complete replacement body as JSON string
     * @return JSON response with status, body, and response time
     */
    @LLMTool(name = "http_put", description = "Make an HTTP PUT request to replace a resource entirely at the URL. Returns status, body, and response time.", maxRetries = 2, retryDelayMs = 1000)
    public String http_put(
            @ToolParam(name = "url", description = "The full URL to PUT to") String url,
            @ToolParam(name = "headers", description = "Optional HTTP headers as JSON string", required = false) String headers,
            @ToolParam(name = "body", description = "Complete replacement body as JSON string", required = false) String body
    ) {
        try {
            Map<String, String> hdrs = parseHeaders(headers);
            setContentTypeIfMissing(hdrs, "application/json");
            long start = System.currentTimeMillis();
            HttpURLConnection conn = openConnection(url, "PUT", hdrs);
            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                try (var w = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                    w.write(body);
                }
            }
            int status = conn.getResponseCode();
            String responseBody = readBody(conn);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("PUT {} -> {} in {}ms", url, status, elapsed);
            return toJson(new HttpResponse(status, responseBody, conn.getContentType(), elapsed));
        } catch (Exception e) {
            log.error("HTTP PUT failed for {}: {}", url, e.getMessage());
            return errorJson("PUT", url, e.getMessage());
        }
    }

    /**
     * Perform HTTP PATCH request to partially update a resource.
     *
     * @param url     Full URL to patch
     * @param headers Optional HTTP headers
     * @param body    Partial update body as JSON string (merge semantics)
     * @return JSON response with status, body, and response time
     */
    @LLMTool(name = "http_patch", description = "Make an HTTP PATCH request to partially update a resource at the URL. Returns status, body, and response time.", maxRetries = 2, retryDelayMs = 1000)
    public String http_patch(
            @ToolParam(name = "url", description = "The full URL to PATCH") String url,
            @ToolParam(name = "headers", description = "Optional HTTP headers as JSON string", required = false) String headers,
            @ToolParam(name = "body", description = "Partial update body as JSON string", required = false) String body
    ) {
        try {
            Map<String, String> hdrs = parseHeaders(headers);
            setContentTypeIfMissing(hdrs, "application/json");
            long start = System.currentTimeMillis();
            HttpURLConnection conn = openConnection(url, "PATCH", hdrs);
            if (body != null && !body.isEmpty()) {
                conn.setDoOutput(true);
                try (var w = new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8)) {
                    w.write(body);
                }
            }
            int status = conn.getResponseCode();
            String responseBody = readBody(conn);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("PATCH {} -> {} in {}ms", url, status, elapsed);
            return toJson(new HttpResponse(status, responseBody, conn.getContentType(), elapsed));
        } catch (Exception e) {
            log.error("HTTP PATCH failed for {}: {}", url, e.getMessage());
            return errorJson("PATCH", url, e.getMessage());
        }
    }

    /**
     * Perform HTTP DELETE request to remove a resource.
     *
     * @param url     Full URL to delete from
     * @param headers Optional HTTP headers (e.g., Authorization)
     * @return JSON response with status, body, and response time
     */
    @LLMTool(name = "http_delete", description = "Make an HTTP DELETE request to remove a resource at the URL. Returns status, body, and response time.", maxRetries = 2, retryDelayMs = 1000)
    public String http_delete(
            @ToolParam(name = "url", description = "The full URL to DELETE") String url,
            @ToolParam(name = "headers", description = "Optional HTTP headers as JSON string", required = false) String headers
    ) {
        try {
            Map<String, String> hdrs = parseHeaders(headers);
            long start = System.currentTimeMillis();
            HttpURLConnection conn = openConnection(url, "DELETE", hdrs);
            int status = conn.getResponseCode();
            String body = readBody(conn);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("DELETE {} -> {} in {}ms", url, status, elapsed);
            return toJson(new HttpResponse(status, body, conn.getContentType(), elapsed));
        } catch (Exception e) {
            log.error("HTTP DELETE failed for {}: {}", url, e.getMessage());
            return errorJson("DELETE", url, e.getMessage());
        }
    }

    // ===== Registration =====

    // Static helper for extracting string args (usable in lambda context)
    private static String extractStr(java.util.Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }

    /**
     * Register all HTTP tools to a ToolRegistry.
     * Adds: http_get, http_post, http_put, http_patch, http_delete
     */
    public static void registerAll(ToolRegistry reg) {
        HttpTools instance = new HttpTools();
        reg.register("http_get", "Make an HTTP GET request to fetch data from a URL. Returns status, body, and response time.",
            (args) -> instance.http_get(
                extractStr(args, "url"),
                extractStr(args, "headers"),
                extractStr(args, "params")
            ), Map.of(
                "url", new ToolRegistry.ParamInfo("url", "The full URL to fetch", false, String.class),
                "headers", new ToolRegistry.ParamInfo("headers", "Optional HTTP headers as JSON string", false, String.class),
                "params", new ToolRegistry.ParamInfo("params", "Optional query parameters as JSON string", false, String.class)
            ), 2, 1000, 2.0, 5000);

        reg.register("http_post", "Make an HTTP POST request to create or submit data. Returns status, body, and response time.",
            (args) -> instance.http_post(extractStr(args, "url"), extractStr(args, "headers"), extractStr(args, "body")),
            Map.of(
                "url", new ToolRegistry.ParamInfo("url", "The full URL to POST to", false, String.class),
                "headers", new ToolRegistry.ParamInfo("headers", "Optional HTTP headers as JSON string", false, String.class),
                "body", new ToolRegistry.ParamInfo("body", "Request body as JSON string", false, String.class)
            ), 2, 1000, 2.0, 5000);

        reg.register("http_put", "Make an HTTP PUT request to replace a resource. Returns status, body, and response time.",
            (args) -> instance.http_put(extractStr(args, "url"), extractStr(args, "headers"), extractStr(args, "body")),
            Map.of(
                "url", new ToolRegistry.ParamInfo("url", "The full URL to PUT to", false, String.class),
                "headers", new ToolRegistry.ParamInfo("headers", "Optional HTTP headers as JSON string", false, String.class),
                "body", new ToolRegistry.ParamInfo("body", "Replacement body as JSON string", false, String.class)
            ), 2, 1000, 2.0, 5000);

        reg.register("http_patch", "Make an HTTP PATCH request to partially update a resource. Returns status, body, and response time.",
            (args) -> instance.http_patch(extractStr(args, "url"), extractStr(args, "headers"), extractStr(args, "body")),
            Map.of(
                "url", new ToolRegistry.ParamInfo("url", "The full URL to PATCH", false, String.class),
                "headers", new ToolRegistry.ParamInfo("headers", "Optional HTTP headers as JSON string", false, String.class),
                "body", new ToolRegistry.ParamInfo("body", "Partial update body as JSON string", false, String.class)
            ), 2, 1000, 2.0, 5000);

        reg.register("http_delete", "Make an HTTP DELETE request to remove a resource. Returns status, body, and response time.",
            (args) -> instance.http_delete(extractStr(args, "url"), extractStr(args, "headers")),
            Map.of(
                "url", new ToolRegistry.ParamInfo("url", "The full URL to DELETE", false, String.class),
                "headers", new ToolRegistry.ParamInfo("headers", "Optional HTTP headers as JSON string", false, String.class)
            ), 2, 1000, 2.0, 5000);

        log.info("Registered 5 HTTP tools (GET, POST, PUT, PATCH, DELETE)");
    }

    // ===== Internal Helpers =====

    private HttpURLConnection openConnection(String urlStr, String method, Map<String, String> headers) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(DEFAULT_READ_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "LLM-Framework/1.0");
        headers.forEach(conn::setRequestProperty);
        return conn;
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode();
        InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (is == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int len;
            while ((len = br.read(buf)) != -1) sb.append(buf, 0, len);
            return sb.toString();
        }
    }

    private String buildUrl(String base, Map<String, String> params) throws Exception {
        if (params == null || params.isEmpty()) return base;
        StringBuilder sb = new StringBuilder(base);
        boolean first = !base.contains("?");
        for (Map.Entry<String, String> e : params.entrySet()) {
            sb.append(first ? "?" : "&");
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return sb.toString();
    }

    private Map<String, String> parseHeaders(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isEmpty() || json.equals("null")) return map;
        try {
            com.google.gson.JsonObject obj = new com.google.gson.Gson().fromJson(json, com.google.gson.JsonObject.class);
            if (obj != null) obj.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().toString().replace("\"", "")));
        } catch (Exception ignored) {}
        return map;
    }

    private Map<String, String> parseQueryParams(String json) {
        return parseHeaders(json);  // Same JSON format works for both
    }

    private void setContentTypeIfMissing(Map<String, String> headers, String ct) {
        if (!headers.containsKey("Content-Type") && !headers.containsKey("content-type")) {
            headers.put("Content-Type", ct);
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v == null ? null : v.toString();
    }

    private String toJson(HttpResponse r) {
        return String.format(
            "{\"status\":%d,\"body\":%s,\"contentType\":\"%s\",\"responseTimeMs\":%d}",
            r.status, jsonEscape(r.body), r.contentType != null ? r.contentType : "", r.responseTimeMs
        );
    }

    private String jsonEscape(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private String errorJson(String method, String url, String msg) {
        return String.format("{\"error\":\"%s %s failed: %s\"}", method, url, msg);
    }
}