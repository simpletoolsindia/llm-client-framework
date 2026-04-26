package in.simpletools.llm.framework.tools;

import com.sun.net.httpserver.HttpServer;
import in.simpletools.llm.framework.tool.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemToolsTest {
    @TempDir
    Path tempDir;

    @Test
    void registersAllSystemTools() {
        ToolRegistry registry = new ToolRegistry();
        SystemTools.registerAll(registry);

        assertEquals(13, registry.getToolCount());
        assertTrue(registry.getToolNames().containsAll(
                java.util.Set.of(
                        "read_file", "write_file", "create_file", "append_file", "delete_file",
                        "list_dir", "find_files", "grep", "path_exists", "file_info",
                        "fetch_webpage", "web_search", "run_bash"
                )
        ));
    }

    @Test
    void fileDirectoryMetaAndShellToolsWorkThroughRegistry() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        SystemTools.registerAll(registry);

        Path nestedFile = tempDir.resolve("nested/example.txt");
        Path touchedFile = tempDir.resolve("created.txt");

        String write = invoke(registry, "write_file", Map.of(
                "path", nestedFile.toString(),
                "content", "alpha\nneedle\n"
        ));
        assertTrue(write.startsWith("Successfully wrote"));

        String read = invoke(registry, "read_file", Map.of("path", nestedFile.toString()));
        assertEquals("alpha\nneedle\n", read);

        String append = invoke(registry, "append_file", Map.of(
                "path", nestedFile.toString(),
                "content", "omega\n"
        ));
        assertTrue(append.startsWith("Appended"));

        String create = invoke(registry, "create_file", Map.of("path", touchedFile.toString()));
        assertTrue(create.startsWith("Created file"));

        String list = invoke(registry, "list_dir", Map.of(
                "path", tempDir.toString(),
                "recursive", "true"
        ));
        assertTrue(list.contains("[DIR]  nested"));
        assertTrue(list.contains("[FILE] example.txt"));

        String found = invoke(registry, "find_files", Map.of(
                "path", tempDir.toString(),
                "pattern", "*.txt"
        ));
        assertTrue(found.contains("example.txt"));
        assertTrue(found.contains("created.txt"));

        String grep = invoke(registry, "grep", Map.of(
                "path", tempDir.toString(),
                "pattern", "needle",
                "file_pattern", "*.txt"
        ));
        assertTrue(grep.contains("example.txt:2: needle"));

        String exists = invoke(registry, "path_exists", Map.of("path", nestedFile.toString()));
        assertEquals("true", exists);

        String info = invoke(registry, "file_info", Map.of("path", nestedFile.toString()));
        assertTrue(info.contains("Type: file"));
        assertTrue(info.contains("Readable: true"));

        String shell = invoke(registry, "run_bash", Map.of(
                "command", "printf system-tools-ok",
                "cwd", tempDir.toString()
        ));
        assertEquals("system-tools-ok\n", shell);

        String deleted = invoke(registry, "delete_file", Map.of("path", touchedFile.toString()));
        assertTrue(deleted.startsWith("Deleted:"));
        assertFalse(Files.exists(touchedFile));
    }

    @Test
    void fetchWebpageToolWorksThroughRegistry() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        SystemTools.registerAll(registry);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/page", exchange -> {
            byte[] body = """
                    <html>
                    <head><style>.hidden{display:none}</style></head>
                    <body><h1>System Tools Fixture</h1><script>ignored()</script><p>Hello &amp; welcome.</p></body>
                    </html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            String body = invoke(registry, "fetch_webpage", Map.of(
                    "url", "http://127.0.0.1:" + server.getAddress().getPort() + "/page",
                    "max_length", 200
            ));
            assertTrue(body.contains("System Tools Fixture"));
            assertTrue(body.contains("Hello & welcome."));
            assertFalse(body.contains("ignored()"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void duckDuckGoWebSearchToolReturnsResults() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        SystemTools.registerAll(registry);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            byte[] body = """
                    <html><body>
                    <div class="result">
                    <a class="result__a" href="https://github.com/simpletoolsindia/llm-client-framework">LLM Client Framework</a>
                    <a class="result__snippet">Unified Java client for LLM providers.</a>
                    </div>
                    </body></html>
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        SystemTools.useDuckDuckGoSearch("http://127.0.0.1:" + server.getAddress().getPort() + "/search?q=%s");
        try {
            String results = invoke(registry, "web_search", Map.of(
                    "query", "simpletoolsindia llm-client-framework",
                    "limit", 3
            ));
            assertTrue(results.startsWith("Search results for:"));
            assertTrue(results.contains("LLM Client Framework"));
            assertTrue(results.contains("Unified Java client for LLM providers."));
            assertTrue(results.contains("URL: https://github.com/simpletoolsindia/llm-client-framework"));
        } finally {
            SystemTools.useDuckDuckGoSearch();
            server.stop(0);
        }
    }

    @Test
    void searxngWebSearchToolReturnsResults() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        SystemTools.registerAll(registry);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            byte[] body = """
                    {
                      "results": [
                        {
                          "title": "SearXNG Result",
                          "url": "https://example.com/searxng",
                          "content": "Result from <b>SearXNG</b> JSON."
                        },
                        {
                          "title": "Second Result",
                          "url": "https://example.com/second",
                          "content": "Another result."
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        SystemTools.useSearxngSearch("http://127.0.0.1:" + server.getAddress().getPort());
        try {
            String results = invoke(registry, "web_search", Map.of(
                    "query", "custom searxng",
                    "limit", 1
            ));
            assertTrue(results.startsWith("Search results for:"));
            assertTrue(results.contains("SearXNG Result"));
            assertTrue(results.contains("Result from SearXNG JSON."));
            assertTrue(results.contains("URL: https://example.com/searxng"));
            assertFalse(results.contains("Second Result"));
        } finally {
            SystemTools.useDuckDuckGoSearch();
            server.stop(0);
        }
    }

    private static String invoke(ToolRegistry registry, String name, Map<String, Object> args) throws Exception {
        Object result = registry.get(name).invoke(args);
        return result == null ? "" : result.toString();
    }
}
