package in.simpletools.llm.framework.history;

import in.simpletools.llm.framework.model.Message;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed conversation history for persistent, multi-session memory.
 * Uses a simple key-value approach with JSON serialization.
 *
 * <pre>
 * {@code
 * // Basic usage
 * RedisHistory history = RedisHistory.create("localhost", 6379, "session-123");
 * history.addUser("Hello!");
 * history.addAssistant("Hi there!");
 *
 * // Get full conversation
 * List<Message> msgs = history.getMessages();
 *
 * // Save and load conversations
 * history.save();       // persist to Redis
 * history.load();       // reload from Redis
 * history.clear();      // clear local + Redis
 * history.delete();     // delete from Redis
 *
 * // User metadata
 * history.set("key", "value");
 * String val = history.get("key");
 * }
 * </pre>
 */
public class RedisHistory implements ConversationHistoryStore {
    private final String redisHost;
    private final int redisPort;
    private final String conversationId;
    private final String historyKey;
    private final String metaKey;
    private final List<Message> messages;
    private final Map<String, String> metadata;
    private final Gson gson;
    private JedisWrapper jedis;

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
        h.jedis = new JedisWrapper() {
            private final redis.clients.jedis.Jedis j = new redis.clients.jedis.Jedis(host, port);
            @Override public String get(String k) { return j.get(k); }
            @Override public String set(String k, String v) { return j.set(k, v); }
            @Override public String setex(String k, long s, String v) { return j.setex(k, s, v); }
            @Override public long expire(String k, long s) { return j.expire(k, s); }
            @Override public long del(String k) { return j.del(k); }
            @Override public boolean ping() { try { j.ping(); return true; } catch(Exception e) { return false; } }
        };
        return h;
    }

    /** Create using a mock/in-memory store (for testing or when Redis unavailable). */
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

    /** Create using HTTP API (e.g., Redis on a remote server with REST interface). */
    public static RedisHistory withHttp(String redisUrl, String conversationId) {
        RedisHistory h = new RedisHistory(redisUrl, 0, conversationId);
        h.redisHost = redisUrl;
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
                    var b = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(redisUrl + "/" + cmd + "/" + k + (v != null ? "/" + v : "")))
                        .GET();
                    if ("POST".equals(cmd) || "SET".equals(cmd)) b = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(redisUrl + "/SET/" + k + "/" + v))
                        .POST(HttpRequest.BodyPublishers.ofString(v));
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

    // ===== Core Operations =====

    /** Add a user message. */
    public void addUser(String content) { add(Message.ofUser(content)); }

    /** Add an assistant message. */
    public void addAssistant(String content) { add(Message.ofAssistant(content)); }

    /** Add a system message. */
    public void addSystem(String content) { add(Message.ofSystem(content)); }

    /** Add a tool result message. */
    public void addTool(String content) { add(Message.ofTool(content)); }

    /** Add any message type. */
    public void add(Message message) {
        messages.add(message);
    }

    /** Get all messages in this conversation. */
    public List<Message> getMessages() { return new ArrayList<>(messages); }

    /** Get messages with full Redis persistence. */
    public List<Message> getMessages(boolean fromRedis) {
        if (fromRedis) load();
        return getMessages();
    }

    public List<Message> getLast(int count) {
        int size = messages.size();
        return messages.subList(Math.max(0, size - count), size);
    }

    /** Clear messages locally and optionally from Redis. */
    public void clear() {
        messages.clear();
    }

    /** Clear from Redis storage as well. */
    public void clearAll() {
        clear();
        if (jedis != null) {
            jedis.del(historyKey);
            jedis.del(metaKey);
        }
    }

    public int size() { return messages.size(); }

    // ===== Persistence =====

    /** Save current messages to Redis. */
    public void save() { save(DEFAULT_TTL_HOURS); }

    /** Save with custom TTL in hours. */
    public void save(int ttlHours) {
        if (jedis == null) return;
        try {
            String json = gson.toJson(messages);
            jedis.setex(historyKey, ttlHours * 3600L, json);
            if (!metadata.isEmpty()) {
                jedis.setex(metaKey, ttlHours * 3600L, gson.toJson(metadata));
            }
        } catch (Exception e) {
            System.err.println("RedisHistory: Failed to save - " + e.getMessage());
        }
    }

    /** Auto-save after each message addition. */
    public void saveOnAdd(int ttlHours) {
        String json = gson.toJson(messages);
        try { jedis.setex(historyKey, ttlHours * 3600L, json); } catch (Exception ignored) {}
    }

    /** Load messages from Redis. */
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
        } catch (Exception e) {
            System.err.println("RedisHistory: Failed to load - " + e.getMessage());
        }
    }

    /** Delete conversation from Redis. */
    public void delete() {
        if (jedis != null) {
            jedis.del(historyKey);
            jedis.del(metaKey);
        }
        messages.clear();
        metadata.clear();
    }

    // ===== Metadata =====

    /** Set a metadata key-value pair. */
    public void set(String key, String value) {
        metadata.put(key, value);
    }

    /** Get a metadata value. */
    public String get(String key) { return metadata.get(key); }

    /** Get all metadata. */
    public Map<String, String> getMetadata() { return new HashMap<>(metadata); }

    // ===== Conversation Management =====

    public String getConversationId() { return conversationId; }

    public boolean isAvailable() {
        if (jedis == null) return false;
        try { return jedis.ping(); } catch (Exception e) { return false; }
    }

    // ===== Bulk Operations =====

    /** List all conversation IDs stored in Redis. */
    public static List<String> listConversations(String host, int port) {
        return listConversations(host, port, "llm:history:*");
    }

    /** List conversation IDs matching a pattern. */
    public static List<String> listConversations(String host, int port, String pattern) {
        List<String> ids = new ArrayList<>();
        try {
            var j = new redis.clients.jedis.Jedis(host, port);
            var keys = j.keys(pattern);
            for (String k : keys) {
                ids.add(k.replace("llm:history:", ""));
            }
            j.close();
        } catch (Exception ignored) {}
        return ids;
    }

    // ===== Convenience Methods =====

    /** Start a new conversation, optionally clearing the old one. */
    public void reset(boolean clearRedis) {
        if (clearRedis) delete(); else clear();
    }

    /** Export conversation as JSON string. */
    public String export() { return gson.toJson(messages); }

    /** Import conversation from JSON string. */
    public void importFrom(String json) {
        try {
            Message[] arr = gson.fromJson(json, Message[].class);
            messages.clear();
            messages.addAll(Arrays.asList(arr));
        } catch (Exception e) {
            System.err.println("RedisHistory: Import failed - " + e.getMessage());
        }
    }
}
