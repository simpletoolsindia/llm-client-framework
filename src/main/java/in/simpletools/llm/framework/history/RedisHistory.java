package in.simpletools.llm.framework.history;

import in.simpletools.llm.framework.model.Message;
import in.simpletools.llm.framework.utils.SimpleLogger;
import com.google.gson.*;
import java.util.*;

/**
 * Redis-backed conversation history for persistent, multi-session memory.
 */
public class RedisHistory implements ConversationHistoryStore {
    private static final SimpleLogger log = SimpleLogger.get("RedisHistory");
    private final String redisHost;
    private final int redisPort;
    private final String conversationId;
    private final String historyKey;
    private final String metaKey;
    private final List<Message> messages;
    private final Map<String, String> metadata;
    private final Gson gson;
    private JedisWrapper jedis;
    private boolean httpMode = false;

    private static final int DEFAULT_TTL_HOURS = 24;

    public interface JedisWrapper {
        String get(String key);
        String set(String key, String value);
        String setex(String key, long seconds, String value);
        long expire(String key, long seconds);
        long del(String key);
        boolean ping();
    }

    /** Create using actual Jedis library (requires jedis dependency). */
    public static RedisHistory withJedis(String host, int port, String conversationId) {
        RedisHistory h = new RedisHistory(host, port, conversationId);
        try {
            Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
            Object jedis = jedisClass.getConstructor(String.class, int.class).newInstance(host, port);
            h.jedis = new JedisWrapper() {
                @Override public String get(String k) {
                    try { return (String) jedisClass.getMethod("get", String.class).invoke(jedis, k); }
                    catch (Exception e) { return null; }
                }
                @Override public String set(String k, String v) {
                    try { return (String) jedisClass.getMethod("set", String.class, String.class).invoke(jedis, k, v); }
                    catch (Exception e) { return null; }
                }
                @Override public String setex(String k, long s, String v) {
                    try { return (String) jedisClass.getMethod("setex", String.class, long.class, String.class).invoke(jedis, k, s, v); }
                    catch (Exception e) { return null; }
                }
                @Override public long expire(String k, long s) {
                    try { return (Long) jedisClass.getMethod("expire", String.class, long.class).invoke(jedis, k, s); }
                    catch (Exception e) { return 0; }
                }
                @Override public long del(String k) {
                    try { return (Long) jedisClass.getMethod("del", String.class).invoke(jedis, k); }
                    catch (Exception e) { return 0; }
                }
                @Override public boolean ping() {
                    try { jedisClass.getMethod("ping").invoke(jedis); return true; }
                    catch (Exception e) { return false; }
                }
            };
        } catch (Exception e) {
            log.error("Jedis not available: {}. Use inMemory() or withHttp() instead.", e.getMessage());
        }
        return h;
    }

    /** Create using a mock/in-memory store. */
    public static RedisHistory inMemory(String conversationId) {
        RedisHistory h = new RedisHistory("localhost", 6379, conversationId);
        Map<String, String> store = new HashMap<>();
        h.jedis = new JedisWrapper() {
            @Override public String get(String k) { return store.get(k); }
            @Override public String set(String k, String v) { return store.put(k, v) != null ? v : null; }
            @Override public String setex(String k, long s, String v) { store.put(k, v); return v; }
            @Override public long expire(String k, long s) { return 1; }
            @Override public long del(String k) { return store.remove(k) != null ? 1 : 0; }
            @Override public boolean ping() { return true; }
        };
        return h;
    }

    /** Create using HTTP API. */
    public static RedisHistory withHttp(String redisUrl, String conversationId) {
        RedisHistory h = new RedisHistory("http", 0, conversationId);
        h.httpMode = true;
        h.jedis = new JedisWrapper() {
            private final java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            @Override public String get(String k) { return httpReq("GET", k, null); }
            @Override public String set(String k, String v) { return httpReq("SET", k, v); }
            @Override public String setex(String k, long s, String v) { return httpReq("SETEX", k + "/" + s, v); }
            @Override public long expire(String k, long s) { return 1; }
            @Override public long del(String k) { httpReq("DEL", k, null); return 1; }
            @Override public boolean ping() { return true; }
            private String httpReq(String cmd, String k, String v) {
                try {
                    java.net.http.HttpRequest.Builder b;
                    if ("POST".equals(cmd) || "SET".equals(cmd) || "SETEX".equals(cmd)) {
                        b = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(redisUrl + "/SET/" + k + "/" + (v != null ? v : "")))
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(v != null ? v : ""));
                    } else {
                        b = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(redisUrl + "/" + cmd + "/" + k))
                            .GET();
                    }
                    var r = client.send(b.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
                    return r.body();
                } catch (Exception e) { return null; }
            }
        };
        return h;
    }

    private RedisHistory(String host, int port, String conversationId) {
        this.redisHost = host;
        this.redisPort = port;
        this.conversationId = conversationId;
        this.historyKey = "llm:history:" + conversationId;
        this.metaKey = "llm:meta:" + conversationId;
        this.messages = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.gson = new Gson();
    }

    public void addUser(String content) { add(Message.ofUser(content)); }
    public void addAssistant(String content) { add(Message.ofAssistant(content)); }
    public void addSystem(String content) { add(Message.ofSystem(content)); }
    public void addTool(String content) { add(Message.ofTool(content)); }
    public void add(Message message) { messages.add(message); }
    public List<Message> getMessages() { return new ArrayList<>(messages); }
    public List<Message> getMessages(boolean fromRedis) { if (fromRedis) load(); return getMessages(); }
    public List<Message> getLast(int count) {
        int size = messages.size();
        return messages.subList(Math.max(0, size - count), size);
    }
    public void clear() { messages.clear(); }
    public void clearAll() {
        messages.clear();
        if (jedis != null) { jedis.del(historyKey); jedis.del(metaKey); }
    }
    public int size() { return messages.size(); }

    public void save() { save(DEFAULT_TTL_HOURS); }
    public void save(int ttlHours) {
        if (jedis == null) return;
        try {
            String json = gson.toJson(messages);
            jedis.setex(historyKey, ttlHours * 3600L, json);
            if (!metadata.isEmpty()) jedis.setex(metaKey, ttlHours * 3600L, gson.toJson(metadata));
        } catch (Exception e) { log.error("Failed to save conversation to Redis: {}", e.getMessage()); }
    }

    public void saveOnAdd(int ttlHours) {
        String json = gson.toJson(messages);
        try { jedis.setex(historyKey, ttlHours * 3600L, json); } catch (Exception ignored) {}
    }

    public void load() {
        if (jedis == null) return;
        try {
            String json = jedis.get(historyKey);
            if (json != null && !json.isEmpty()) {
                Message[] arr = gson.fromJson(json, Message[].class);
                messages.clear();
                messages.addAll(Arrays.asList(arr));
            }
            String meta = jedis.get(metaKey);
            if (meta != null && !meta.isEmpty()) {
                Map<?, ?> m = gson.fromJson(meta, Map.class);
                metadata.clear();
                m.forEach((k, v) -> metadata.put(k.toString(), v.toString()));
            }
        } catch (Exception e) { log.error("Failed to load conversation from Redis: {}", e.getMessage()); }
    }

    public void delete() {
        if (jedis != null) { jedis.del(historyKey); jedis.del(metaKey); }
        messages.clear();
        metadata.clear();
    }

    public void set(String key, String value) { metadata.put(key, value); }
    public String get(String key) { return metadata.get(key); }
    public Map<String, String> getMetadata() { return new HashMap<>(metadata); }
    public String getConversationId() { return conversationId; }
    public boolean isAvailable() { return jedis != null && jedis.ping(); }

    public static List<String> listConversations(String host, int port) { return listConversations(host, port, "llm:history:*"); }

    public static List<String> listConversations(String host, int port, String pattern) {
        List<String> ids = new ArrayList<>();
        try {
            Class<?> jedisClass = Class.forName("redis.clients.jedis.Jedis");
            Object j = jedisClass.getConstructor(String.class, int.class).newInstance(host, port);
            Object keys = jedisClass.getMethod("keys", String.class).invoke(j, pattern);
            for (Object k : (Iterable<?>) keys) ids.add(k.toString().replace("llm:history:", ""));
            jedisClass.getMethod("close").invoke(j);
        } catch (Exception e) { throw new UnsupportedOperationException("Jedis not on classpath.", e); }
        return ids;
    }

    public void reset(boolean clearRedis) { if (clearRedis) delete(); else clear(); }
    public String export() { return gson.toJson(messages); }
    public void importFrom(String json) {
        try {
            Message[] arr = gson.fromJson(json, Message[].class);
            messages.clear();
            messages.addAll(Arrays.asList(arr));
        } catch (Exception e) { log.error("Failed to import conversation from JSON: {}", e.getMessage()); }
    }
}
