package in.simpletools.llm.framework.tools;

import in.simpletools.llm.framework.tool.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * Built-in system tools for file operations, web search, and shell commands.
 * Works cross-platform (Windows, Linux, macOS).
 *
 * <pre>
 * {@code
 * // Register all built-in tools
 * SystemTools.registerAll(registry);
 *
 * // Or register selectively
 * SystemTools.registerFileTools(registry);
 * SystemTools.registerWebTools(registry);
 * SystemTools.registerShellTools(registry);
 * }
 * </pre>
 */
public class SystemTools {

    // ===== File Tools =====

    @LLMTool(name = "read_file", description = "Read the entire content of a file. Returns file contents as a string.")
    public static String readFile(@ToolParam(name = "path", description = "Absolute or relative file path to read") String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) return "Error: File not found: " + path;
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }

    @LLMTool(name = "write_file", description = "Write or overwrite content to a file. Creates parent directories if needed.")
    public static String writeFile(
            @ToolParam(name = "path", description = "File path to write to") String path,
            @ToolParam(name = "content", description = "Content to write") String content) {
        try {
            Path p = Paths.get(path);
            Files.createDirectories(p.getParent());
            Files.writeString(p, content, StandardCharsets.UTF_8);
            return "Successfully wrote " + content.length() + " characters to " + path;
        } catch (Exception e) {
            return "Error writing file: " + e.getMessage();
        }
    }

    @LLMTool(name = "create_file", description = "Create a new empty file, or touch an existing file to update its modification time.")
    public static String createFile(@ToolParam(name = "path", description = "File path to create") String path) {
        try {
            Path p = Paths.get(path);
            Files.createDirectories(p.getParent());
            if (Files.exists(p)) {
                Files.setLastModifiedTime(p, FileTime.fromMillis(System.currentTimeMillis()));
                return "File already exists, updated timestamp: " + p.toAbsolutePath();
            }
            Files.createFile(p);
            return "Created file: " + p.toAbsolutePath();
        } catch (Exception e) {
            return "Error creating file: " + e.getMessage();
        }
    }

    @LLMTool(name = "append_file", description = "Append content to a file. Creates the file if it does not exist.")
    public static String appendFile(
            @ToolParam(name = "path", description = "File path to append to") String path,
            @ToolParam(name = "content", description = "Content to append") String content) {
        try {
            Path p = Paths.get(path);
            Files.createDirectories(p.getParent());
            Files.writeString(p, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Appended " + content.length() + " characters to " + path;
        } catch (Exception e) {
            return "Error appending to file: " + e.getMessage();
        }
    }

    @LLMTool(name = "delete_file", description = "Delete a file or empty directory.")
    public static String deleteFile(@ToolParam(name = "path", description = "Path to delete") String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) return "Error: Path does not exist: " + path;
            Files.delete(p);
            return "Deleted: " + p.toAbsolutePath();
        } catch (Exception e) {
            return "Error deleting: " + e.getMessage();
        }
    }

    // ===== Directory Tools =====

    @LLMTool(name = "list_dir", description = "List files and directories at a given path. Supports recursive listing.")
    public static String listDir(
            @ToolParam(name = "path", description = "Directory path to list (defaults to .)", required = false) String path,
            @ToolParam(name = "recursive", description = "If true, list subdirectories recursively", required = false) String recursive) {
        try {
            Path dir = (path == null || path.isEmpty()) ? Paths.get(".") : Paths.get(path);
            if (!Files.exists(dir)) return "Error: Directory not found: " + path;
            if (!Files.isDirectory(dir)) return "Error: Not a directory: " + path;

            boolean rec = Boolean.parseBoolean(recursive);
            StringBuilder sb = new StringBuilder();
            listDirRecursive(dir, sb, "", rec, 0);
            return sb.toString();
        } catch (Exception e) {
            return "Error listing directory: " + e.getMessage();
        }
    }

    private static void listDirRecursive(Path dir, StringBuilder sb, String indent, boolean recursive, int depth) {
        try {
            if (depth > 10) { sb.append(indent).append("(max depth reached)\n"); return; }
            try (var stream = Files.list(dir)) {
                List<Path> entries = stream.sorted().collect(Collectors.toList());
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    boolean isDir = Files.isDirectory(entry);
                    sb.append(indent).append(isDir ? "[DIR]  " : "[FILE] ").append(name).append("\n");
                    if (isDir && recursive) {
                        listDirRecursive(entry, sb, indent + "    ", recursive, depth + 1);
                    }
                }
            }
        } catch (IOException e) {
            sb.append(indent).append("(error reading: ").append(e.getMessage()).append(")\n");
        }
    }

    @LLMTool(name = "find_files", description = "Recursively find files matching a glob pattern in a directory tree.")
    public static String findFiles(
            @ToolParam(name = "path", description = "Root directory to search") String path,
            @ToolParam(name = "pattern", description = "Glob pattern (e.g., *.java, **/*.txt)") String pattern) {
        try {
            Path root = Paths.get(path);
            if (!Files.exists(root)) return "Error: Directory not found: " + path;

            StringBuilder sb = new StringBuilder();
            try (var walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> matchesGlob(p.getFileName().toString(), pattern))
                    .limit(500)
                    .forEach(p -> sb.append(p.toAbsolutePath()).append("\n"));
            }
            String result = sb.toString();
            return result.isEmpty() ? "No files matching '" + pattern + "' found in " + path : result;
        } catch (Exception e) {
            return "Error searching files: " + e.getMessage();
        }
    }

    private static boolean matchesGlob(String name, String pattern) {
        String regex = pattern
            .replace(".", "\\.")
            .replace("**/", "(.*/)?")
            .replace("**", ".*")
            .replace("*", "[^/]*")
            .replace("?", ".");
        return Pattern.compile(regex).matcher(name).matches();
    }

    @LLMTool(name = "grep", description = "Search for text within files. Supports regex and file-type filtering.")
    public static String grep(
            @ToolParam(name = "path", description = "Root directory or file to search") String path,
            @ToolParam(name = "pattern", description = "Text or regex pattern to search for") String pattern,
            @ToolParam(name = "file_pattern", description = "Only search in files matching this glob (e.g., *.java)", required = false) String filePattern) {
        try {
            Path root = Paths.get(path);
            if (!Files.exists(root)) return "Error: Path not found: " + path;

            StringBuilder sb = new StringBuilder();
            Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            int maxResults = 100;

            if (Files.isRegularFile(root)) {
                searchFile(root, regex, sb, 0);
            } else {
                try (var walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                        .filter(p -> filePattern == null || filePattern.isEmpty() || matchesGlob(p.getFileName().toString(), filePattern))
                        .limit(200)
                        .forEach(p -> {
                            if (sb.length() < 10000 && maxResults > 0) {
                                searchFile(p, regex, sb, maxResults);
                            }
                        });
                }
            }
            String result = sb.toString();
            return result.isEmpty() ? "No matches found for '" + pattern + "'" : result;
        } catch (Exception e) {
            return "Error searching: " + e.getMessage();
        }
    }

    private static void searchFile(Path file, Pattern regex, StringBuilder sb, int maxResults) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null && maxResults > 0) {
                lineNum++;
                if (regex.matcher(line).find()) {
                    String display = line.length() > 200 ? line.substring(0, 200) + "..." : line;
                    sb.append(file.getFileName()).append(":").append(lineNum).append(": ").append(display).append("\n");
                    maxResults--;
                }
            }
        } catch (IOException ignored) {}
    }

    // ===== Web Tools =====

    @LLMTool(name = "fetch_webpage", description = "Fetch and return the text content of a webpage. Strips HTML tags.")
    public static String fetchWebpage(
            @ToolParam(name = "url", description = "Full URL of the webpage to fetch") String url,
            @ToolParam(name = "max_length", description = "Maximum characters to return", required = false) Integer maxLength) {
        try {
            URL target = URI.create(url).toURL();
            HttpURLConnection conn = (HttpURLConnection) target.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; LLM-Tools/1.0)");
            conn.setConnectTimeout;
            conn.setReadTimeout;

            int code = conn.getResponseCode();
            if (code != 200) return "Error: HTTP " + code;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }

            String html = sb.toString();
            String text = stripHtml(html);

            int limit = (maxLength != null && maxLength > 0) ? Math.min(maxLength, 50000) : 10000;
            if (text.length() > limit) text = text.substring(0, limit) + "\n... (truncated)";

            return text;
        } catch (Exception e) {
            return "Error fetching webpage: " + e.getMessage();
        }
    }

    private static String stripHtml(String html) {
        if (html == null) return "";
        html = html.replaceAll("(?s)<script[^>]*>.*?</script>", "");
        html = html.replaceAll("(?s)<style[^>]*>.*?</style>", "");
        html = html.replaceAll("<[^>]+>", " ");
        html = html.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                   .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                   .replace("&apos;", "'");
        html = html.replaceAll("\\s+", " ").trim();
        return html;
    }

    @LLMTool(name = "web_search", description = "Perform a web search and return top results with titles and snippets.")
    public static String webSearch(
            @ToolParam(name = "query", description = "Search query") String query,
            @ToolParam(name = "limit", description = "Maximum number of results", required = false) Integer limit) {
        try {
            int max = (limit != null && limit > 0) ? Math.min(limit, 20) : 10;
            String searchUrl = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

            String html = fetchWebpage(searchUrl, 30000);
            if (html.startsWith("Error:")) return html;

            StringBuilder sb = new StringBuilder();
            sb.append("Search results for: ").append(query).append("\n\n");

            Pattern resultPat = Pattern.compile(
                "<a class=\"result__a\" href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?<a class=\"result__snippet\"[^>]*>(.*?)</a>",
                Pattern.DOTALL);
            Pattern linkPat = Pattern.compile("<a class=\"result__a\" href=\"([^\"]+)\"[^>]*>(.*?)</a>");

            Matcher m = resultPat.matcher(html);
            int count = 0;
            Set<String> seen = new LinkedHashSet<>();
            while (m.find() && count < max) {
                String title = stripHtml(m.group(2)).trim();
                String snippet = stripHtml(m.group(3)).trim();
                String link = m.group(1);
                if (!seen.contains(link) && !title.isEmpty()) {
                    seen.add(link);
                    sb.append(count + 1).append(". ").append(title).append("\n");
                    sb.append("   ").append(snippet).append("\n");
                    sb.append("   URL: ").append(link).append("\n\n");
                    count++;
                }
            }

            if (count == 0) {
                Matcher lm = linkPat.matcher(html);
                while (lm.find() && count < max) {
                    String title = stripHtml(lm.group(2)).trim();
                    String link = lm.group(1);
                    if (!seen.contains(link) && !title.isEmpty() && !title.contains("More results")) {
                        seen.add(link);
                        sb.append(count + 1).append(". ").append(title).append("\n   URL: ").append(link).append("\n\n");
                        count++;
                    }
                }
            }

            return sb.length() > 50 ? sb.toString() : "No search results found for: " + query;
        } catch (Exception e) {
            return "Error performing web search: " + e.getMessage();
        }
    }

    // ===== Shell / Bash Tools =====

    @LLMTool(name = "run_bash", description = "Execute a shell command (bash on Linux/macOS, cmd.exe on Windows). Returns stdout and stderr.")
    public static String runBash(
            @ToolParam(name = "command", description = "Shell command to execute") String command,
            @ToolParam(name = "cwd", description = "Working directory (optional)", required = false) String cwd) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            boolean isWindows = os.contains("win");

            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("/bin/sh", "-c", command);
            }

            pb.redirectErrorStream(false);
            if (cwd != null && !cwd.isEmpty()) pb.directory(new File(cwd));
            pb.environment().put("TERM", "dumb");

            Process p = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = out.readLine()) != null) output.append(line).append("\n");
            }

            try (BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = err.readLine()) != null) output.append("[stderr] ").append(line).append("\n");
            }

            int exitCode = p.waitFor();
            if (exitCode != 0 && output.length() == 0) {
                output.append("(command exited with code ").append(exitCode).append(")");
            }

            String result = output.toString();
            if (result.length() > 10000) result = result.substring(0, 10000) + "\n... (output truncated)";
            return result;
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    @LLMTool(name = "path_exists", description = "Check if a file or directory path exists.")
    public static String pathExists(@ToolParam(name = "path", description = "Path to check") String path) {
        return Files.exists(Paths.get(path)) ? "true" : "false";
    }

    @LLMTool(name = "file_info", description = "Get file or directory metadata: size, modified time, type.")
    public static String fileInfo(@ToolParam(name = "path", description = "Path to inspect") String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) return "Error: Path not found: " + path;

            StringBuilder sb = new StringBuilder();
            sb.append("Path: ").append(p.toAbsolutePath()).append("\n");
            sb.append("Type: ").append(Files.isDirectory(p) ? "directory" : "file").append("\n");
            sb.append("Size: ").append(Files.size(p)).append(" bytes\n");
            sb.append("Modified: ").append(Files.getLastModifiedTime(p)).append("\n");
            sb.append("Readable: ").append(Files.isReadable(p)).append("\n");
            sb.append("Writable: ").append(Files.isWritable(p)).append("\n");
            return sb.toString();
        } catch (Exception e) {
            return "Error getting file info: " + e.getMessage();
        }
    }

    // ===== Auto-registration Helpers =====

    public static void registerAll(ToolRegistry registry) {
        registry.registerAll(new SystemTools());
    }

    public static void registerFileTools(ToolRegistry registry) {
        registry.registerAll(new FileTools());
    }

    public static void registerWebTools(ToolRegistry registry) {
        registry.registerAll(new WebTools());
    }

    public static void registerShellTools(ToolRegistry registry) {
        registry.registerAll(new ShellTools());
    }

    // ===== Selective Registration Sub-Classes =====

    public static class FileTools {
        @LLMTool(name = "read_file", description = "Read a file.")
        public String readFile(@ToolParam(name = "path") String path) { return SystemTools.readFile(path); }

        @LLMTool(name = "write_file", description = "Write to a file.")
        public String writeFile(@ToolParam(name = "path") String path, @ToolParam(name = "content") String content) {
            return SystemTools.writeFile(path, content);
        }

        @LLMTool(name = "create_file", description = "Create an empty file.")
        public String createFile(@ToolParam(name = "path") String path) { return SystemTools.createFile(path); }

        @LLMTool(name = "append_file", description = "Append to a file.")
        public String appendFile(@ToolParam(name = "path") String path, @ToolParam(name = "content") String content) {
            return SystemTools.appendFile(path, content);
        }

        @LLMTool(name = "delete_file", description = "Delete a file.")
        public String deleteFile(@ToolParam(name = "path") String path) { return SystemTools.deleteFile(path); }

        @LLMTool(name = "list_dir", description = "List a directory.")
        public String listDir(@ToolParam(name = "path", required = false) String path,
                              @ToolParam(name = "recursive", required = false) String recursive) {
            return SystemTools.listDir(path, recursive);
        }

        @LLMTool(name = "find_files", description = "Find files.")
        public String findFiles(@ToolParam(name = "path") String path, @ToolParam(name = "pattern") String pattern) {
            return SystemTools.findFiles(path, pattern);
        }

        @LLMTool(name = "grep", description = "Search file contents.")
        public String grep(@ToolParam(name = "path") String path,
                          @ToolParam(name = "pattern") String pattern,
                          @ToolParam(name = "file_pattern", required = false) String filePattern) {
            return SystemTools.grep(path, pattern, filePattern);
        }

        @LLMTool(name = "path_exists", description = "Check if path exists.")
        public String pathExists(@ToolParam(name = "path") String path) { return SystemTools.pathExists(path); }

        @LLMTool(name = "file_info", description = "Get file metadata.")
        public String fileInfo(@ToolParam(name = "path") String path) { return SystemTools.fileInfo(path); }
    }

    public static class WebTools {
        @LLMTool(name = "fetch_webpage", description = "Fetch a webpage.")
        public String fetchWebpage(@ToolParam(name = "url") String url,
                                   @ToolParam(name = "max_length", required = false) Integer maxLength) {
            return SystemTools.fetchWebpage(url, maxLength);
        }

        @LLMTool(name = "web_search", description = "Search the web.")
        public String webSearch(@ToolParam(name = "query") String query,
                                @ToolParam(name = "limit", required = false) Integer limit) {
            return SystemTools.webSearch(query, limit);
        }
    }

    public static class ShellTools {
        @LLMTool(name = "run_bash", description = "Run a shell command.")
        public String runBash(@ToolParam(name = "command") String command,
                              @ToolParam(name = "cwd", required = false) String cwd) {
            return SystemTools.runBash(command, cwd);
        }
    }
}
