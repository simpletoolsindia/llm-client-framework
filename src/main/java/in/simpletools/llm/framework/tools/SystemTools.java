package in.simpletools.llm.framework.tools;

import in.simpletools.llm.framework.tool.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Built-in system tools for file operations, web search, and shell commands.
 * All tools work cross-platform on Windows, macOS, and Linux.
 *
 * <p>Register tools with a ToolRegistry:
 *
 * <pre>{@code
 * ToolRegistry registry = new ToolRegistry();
 * SystemTools.registerAll(registry);        // register all 13 tools
 * SystemTools.registerFileTools(registry);  // file tools only
 * SystemTools.registerWebTools(registry);   // web tools only
 * SystemTools.registerShellTools(registry); // bash tool only
 * }</pre>
 *
 * <p>Or register via LLMClient:
 *
 * <pre>{@code
 * LLMClient client = LLMClient.ollama("gemma4:latest");
 * client.withSystemTools();               // all tools
 * client.withSystemTools("file");          // file tools only
 * }</pre>
 *
 * <p><b>Available Tools:</b>
 * <ul>
 *   <li><b>File:</b> read_file, write_file, create_file, append_file, delete_file</li>
 *   <li><b>Directory:</b> list_dir, find_files, grep</li>
 *   <li><b>Meta:</b> path_exists, file_info</li>
 *   <li><b>Web:</b> web_search, fetch_webpage</li>
 *   <li><b>Shell:</b> run_bash</li>
 * </ul>
 */
public class SystemTools {

    // ===== FILE TOOLS =====

    /**
     * Read the entire contents of a file as a string.
     *
     * @param path absolute or relative path to the file
     * @return file contents, or an error message string
     */
    @LLMTool(name = "read_file", description = "Read the entire contents of a file. Returns the full text of the file.")
    public static String readFile(
            @ToolParam(name = "path", description = "Absolute or relative path to the file to read") String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                return "Error: File not found: " + path;
            }
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Error reading file '" + path + "': " + e.getMessage();
        }
    }

    /**
     * Write or overwrite content to a file.
     * Creates parent directories automatically if they don't exist.
     *
     * @param path    destination file path
     * @param content text content to write
     * @return success or error message
     */
    @LLMTool(name = "write_file", description = "Write or overwrite content to a file. Creates parent directories if needed.")
    public static String writeFile(
            @ToolParam(name = "path", description = "File path to write to") String path,
            @ToolParam(name = "content", description = "Text content to write to the file") String content) {
        try {
            Path p = Paths.get(path);
            // Ensure parent directories exist
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, content, StandardCharsets.UTF_8);
            return "Successfully wrote " + content.length() + " characters to " + path;
        } catch (Exception e) {
            return "Error writing file '" + path + "': " + e.getMessage();
        }
    }

    /**
     * Create a new empty file, or update the modification time of an existing file ("touch").
     *
     * @param path file path to create or touch
     * @return success or error message
     */
    @LLMTool(name = "create_file", description = "Create a new empty file. If the file already exists, updates its modification timestamp.")
    public static String createFile(
            @ToolParam(name = "path", description = "File path to create or touch") String path) {
        try {
            Path p = Paths.get(path);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            if (Files.exists(p)) {
                // Touch: update modification time
                Files.setLastModifiedTime(p, FileTime.fromMillis(System.currentTimeMillis()));
                return "File already exists, updated timestamp: " + p.toAbsolutePath();
            }
            Files.createFile(p);
            return "Created file: " + p.toAbsolutePath();
        } catch (Exception e) {
            return "Error creating file '" + path + "': " + e.getMessage();
        }
    }

    /**
     * Append content to the end of a file.
     * Creates the file if it does not already exist.
     *
     * @param path    file path to append to
     * @param content text content to append
     * @return success or error message
     */
    @LLMTool(name = "append_file", description = "Append content to the end of a file. Creates the file if it does not exist.")
    public static String appendFile(
            @ToolParam(name = "path", description = "File path to append to") String path,
            @ToolParam(name = "content", description = "Text content to append to the file") String content) {
        try {
            Path p = Paths.get(path);
            if (p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return "Appended " + content.length() + " characters to " + path;
        } catch (Exception e) {
            return "Error appending to file '" + path + "': " + e.getMessage();
        }
    }

    /**
     * Delete a file or an empty directory.
     *
     * @param path file or directory path to delete
     * @return success or error message
     */
    @LLMTool(name = "delete_file", description = "Delete a file or an empty directory.")
    public static String deleteFile(
            @ToolParam(name = "path", description = "File or directory path to delete") String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                return "Error: Path does not exist: " + path;
            }
            Files.delete(p);
            return "Deleted: " + p.toAbsolutePath();
        } catch (Exception e) {
            return "Error deleting '" + path + "': " + e.getMessage();
        }
    }

    // ===== DIRECTORY TOOLS =====

    /**
     * List files and directories within a given path.
     * Optionally lists subdirectories recursively.
     *
     * @param path      directory path to list (defaults to current directory ".")
     * @param recursive if "true", lists subdirectories recursively
     * @return formatted directory listing, or error message
     */
    @LLMTool(name = "list_dir",
            description = "List files and directories at a given path. Supports recursive listing.")
    public static String listDir(
            @ToolParam(name = "path", description = "Directory path to list (defaults to current dir)", required = false) String path,
            @ToolParam(name = "recursive", description = "If true, list subdirectories recursively", required = false) String recursive) {
        try {
            Path dir = (path == null || path.trim().isEmpty()) ? Paths.get(".") : Paths.get(path);
            if (!Files.exists(dir)) {
                return "Error: Directory not found: " + path;
            }
            if (!Files.isDirectory(dir)) {
                return "Error: Not a directory: " + path;
            }

            boolean rec = Boolean.parseBoolean(recursive);
            StringBuilder sb = new StringBuilder();
            listDirRecursive(dir, sb, "", rec, 0);
            return sb.toString();
        } catch (Exception e) {
            return "Error listing directory '" + path + "': " + e.getMessage();
        }
    }

    /**
     * Recursively list directory contents with indentation.
     * Capped at 10 levels deep to prevent infinite recursion.
     */
    private static void listDirRecursive(Path dir, StringBuilder sb, String indent,
                                       boolean recursive, int depth) {
        try {
            if (depth > 10) {
                sb.append(indent).append("(max depth reached, stopping)\n");
                return;
            }
            try (var stream = Files.list(dir)) {
                List<Path> entries = stream.sorted().collect(Collectors.toList());
                for (Path entry : entries) {
                    String name = entry.getFileName().toString();
                    boolean isDir = Files.isDirectory(entry);
                    sb.append(indent)
                      .append(isDir ? "[DIR]  " : "[FILE] ")
                      .append(name)
                      .append("\n");
                    if (isDir && recursive) {
                        listDirRecursive(entry, sb, indent + "    ", recursive, depth + 1);
                    }
                }
            }
        } catch (IOException e) {
            sb.append(indent).append("(error reading: ").append(e.getMessage()).append(")\n");
        }
    }

    /**
     * Find all files matching a glob pattern within a directory tree.
     * Supports patterns like "*.java", "star-star/*.txt", "src/star-star/*.java".
     *
     * @param path    root directory to search from
     * @param pattern glob pattern (e.g., "*.java", "star-star-slash*.md")
     * @return list of matching file paths, one per line
     */
    @LLMTool(name = "find_files",
            description = "Recursively find all files matching a glob pattern in a directory tree. " +
                    "Examples: *.java finds all Java files, star-star/*.txt finds all text files.")
    public static String findFiles(
            @ToolParam(name = "path", description = "Root directory to search from") String path,
            @ToolParam(name = "pattern", description = "Glob pattern (e.g., *.java, star-star/*.txt, *.md)") String pattern) {
        try {
            Path root = Paths.get(path);
            if (!Files.exists(root)) {
                return "Error: Directory not found: " + path;
            }

            StringBuilder sb = new StringBuilder();
            int maxResults = 500;
            int[] count = {0};

            try (var walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> matchesGlob(p.getFileName().toString(), pattern))
                    .limit(maxResults)
                    .forEach(p -> {
                        sb.append(p.toAbsolutePath()).append("\n");
                        count[0]++;
                    });
            }

            if (count[0] == 0) {
                return "No files matching pattern '" + pattern + "' found in " + path;
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error searching for files in '" + path + "': " + e.getMessage();
        }
    }

    /**
     * Convert a glob pattern to a regex for matching.
     * Handles: * (any chars), ** (any path), ? (single char), . (literal dot).
     */
    private static boolean matchesGlob(String name, String pattern) {
        String regex = pattern
                .replace(".", "\\.")      // escape dots
                .replace("**/", "(.*/)?")  // **/ = any subdirectory
                .replace("**", ".*")       // ** = any characters
                .replace("*", "[^/]*")    // * = any chars except slash
                .replace("?", ".");       // ? = single char
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(name).matches();
    }

    /**
     * Search for text content within files using regex or plain text.
     * Searches recursively within a directory, or within a single file.
     *
     * @param path        directory or file path to search
     * @param pattern     text or regex pattern to search for
     * @param filePattern optional glob filter for file types (e.g., "*.java")
     * @return matching lines with filename:lineNumber:content format
     */
    @LLMTool(name = "grep",
            description = "Search for text within files. Supports regex patterns and optional file-type filtering. " +
                    "Returns matching lines with filename and line numbers.")
    public static String grep(
            @ToolParam(name = "path", description = "Directory or file path to search within") String path,
            @ToolParam(name = "pattern", description = "Text or regex pattern to search for") String pattern,
            @ToolParam(name = "file_pattern",
                    description = "Optional glob filter for file types (e.g., *.java, *.txt)", required = false) String filePattern) {
        try {
            Path root = Paths.get(path);
            if (!Files.exists(root)) {
                return "Error: Path not found: " + path;
            }

            StringBuilder sb = new StringBuilder();
            Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            int maxResults = 100;

            if (Files.isRegularFile(root)) {
                // Search within a single file
                searchFile(root, regex, sb, maxResults);
            } else {
                // Search within a directory tree
                try (var walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                        .filter(p -> filePattern == null || filePattern.trim().isEmpty()
                                || matchesGlob(p.getFileName().toString(), filePattern))
                        .limit(200)
                        .forEach(p -> {
                            if (sb.length() < 10000 && maxResults > 0) {
                                searchFile(p, regex, sb, maxResults);
                            }
                        });
                }
            }

            String result = sb.toString();
            return result.isEmpty()
                    ? "No matches found for '" + pattern + "' in " + path
                    : result.trim();
        } catch (Exception e) {
            return "Error searching for '" + pattern + "': " + e.getMessage();
        }
    }

    /**
     * Search a single file for matching lines.
     * Limits output to prevent huge responses.
     */
    private static void searchFile(Path file, Pattern regex, StringBuilder sb, int maxResults) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null && maxResults > 0) {
                lineNum++;
                if (regex.matcher(line).find()) {
                    // Truncate very long lines for readability
                    String display = line.length() > 200 ? line.substring(0, 200) + "..." : line;
                    sb.append(file.getFileName())
                      .append(":")
                      .append(lineNum)
                      .append(": ")
                      .append(display)
                      .append("\n");
                    maxResults--;
                }
            }
        } catch (IOException ignored) {
            // Skip files that can't be read
        }
    }

    // ===== META TOOLS =====

    /**
     * Check whether a file or directory path exists.
     *
     * @param path path to check
     * @return "true" if the path exists, "false" otherwise
     */
    @LLMTool(name = "path_exists",
            description = "Check if a file or directory path exists. Returns 'true' or 'false'.")
    public static String pathExists(
            @ToolParam(name = "path", description = "File or directory path to check") String path) {
        return Files.exists(Paths.get(path)) ? "true" : "false";
    }

    /**
     * Get metadata about a file or directory: type, size, modification time, permissions.
     *
     * @param path file or directory path to inspect
     * @return formatted metadata string
     */
    @LLMTool(name = "file_info",
            description = "Get detailed metadata about a file or directory: type, size in bytes, " +
                    "last modified time, and read/write permissions.")
    public static String fileInfo(
            @ToolParam(name = "path", description = "File or directory path to inspect") String path) {
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                return "Error: Path not found: " + path;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Path: ").append(p.toAbsolutePath()).append("\n");
            sb.append("Type: ").append(Files.isDirectory(p) ? "directory" : "file").append("\n");
            sb.append("Size: ").append(Files.size(p)).append(" bytes\n");
            sb.append("Modified: ").append(Files.getLastModifiedTime(p)).append("\n");
            sb.append("Readable: ").append(Files.isReadable(p)).append("\n");
            sb.append("Writable: ").append(Files.isWritable(p)).append("\n");
            return sb.toString();
        } catch (Exception e) {
            return "Error getting file info for '" + path + "': " + e.getMessage();
        }
    }

    // ===== WEB TOOLS =====

    /**
     * Fetch and extract text content from a webpage.
     * Strips HTML tags, scripts, styles, and decodes common HTML entities.
     *
     * @param url       full URL of the webpage to fetch
     * @param maxLength maximum number of characters to return (default 10000)
     * @return extracted text content, or error message
     */
    @LLMTool(name = "fetch_webpage",
            description = "Fetch and extract readable text content from a webpage. " +
                    "Strips HTML tags, scripts, and styles. Returns plain text.")
    public static String fetchWebpage(
            @ToolParam(name = "url", description = "Full URL of the webpage to fetch") String url,
            @ToolParam(name = "max_length",
                    description = "Maximum number of characters to return (default 10000)", required = false) Integer maxLength) {
        HttpURLConnection conn = null;
        try {
            URL target = URI.create(url).toURL();
            conn = (HttpURLConnection) target.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; LLM-Tools/1.0)");
            conn.setConnectTimeout(10_000);  // 10 second connection timeout
            conn.setReadTimeout(10_000);     // 10 second read timeout

            int httpCode = conn.getResponseCode();
            if (httpCode != 200) {
                return "Error: HTTP " + httpCode + " for URL: " + url;
            }

            // Read response body
            StringBuilder html = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    html.append(line).append("\n");
                }
            }

            // Strip HTML to get plain text
            String text = stripHtml(html.toString());

            // Apply length limit
            int limit = (maxLength != null && maxLength > 0)
                    ? Math.min(maxLength, 50000)
                    : 10000;
            if (text.length() > limit) {
                text = text.substring(0, limit) + "\n... (content truncated to " + limit + " characters)";
            }

            return text;
        } catch (Exception e) {
            return "Error fetching webpage '" + url + "': " + e.getMessage();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Strip HTML tags, scripts, styles, and decode HTML entities to produce plain text.
     */
    private static String stripHtml(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        // Remove script and style blocks first (before general tag removal)
        html = html.replaceAll("(?s)<script[^>]*>.*?</script>", "");
        html = html.replaceAll("(?s)<style[^>]*>.*?</style>", "");
        // Remove all remaining HTML tags
        html = html.replaceAll("<[^>]+>", " ");
        // Decode common HTML entities
        html = html.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&copy;", "(c)")
                .replace("&reg;", "(R)")
                .replace("&trade;", "(TM)");
        // Normalize whitespace: collapse multiple spaces/newlines
        html = html.replaceAll("\\s+", " ").trim();
        return html;
    }

    /**
     * Perform a web search and return formatted results with titles, snippets, and URLs.
     * Uses DuckDuckGo HTML as the search backend.
     *
     * @param query search query string
     * @param limit  maximum number of results to return (default 10, max 20)
     * @return formatted search results
     */
    @LLMTool(name = "web_search",
            description = "Perform a web search and return formatted results with titles, snippets, " +
                    "and URLs. Returns top matching web pages for the query.")
    public static String webSearch(
            @ToolParam(name = "query", description = "The search query") String query,
            @ToolParam(name = "limit", description = "Maximum number of results (default 10, max 20)", required = false) Integer limit) {
        try {
            int maxResults = (limit != null && limit > 0) ? Math.min(limit, 20) : 10;

            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String searchUrl = System.getProperty(
                    "simpletools.webSearchUrlTemplate",
                    "https://html.duckduckgo.com/html/?q=%s"
            ).formatted(encodedQuery);

            // Fetch the raw search results page so the parser can inspect result links.
            String html = fetchRaw(searchUrl);
            if (html.startsWith("Error:")) {
                return "Error performing web search: could not fetch search page. " + html;
            }
            if (html.contains("anomaly-modal") || html.contains("Unfortunately, bots use DuckDuckGo too")) {
                return "Error performing web search: search provider blocked automated requests.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Search results for: \"").append(query).append("\"\n\n");

            // Regex patterns to extract results from DuckDuckGo HTML
            // Primary pattern: result with snippet
            Pattern resultWithSnippet = Pattern.compile(
                    "<a class=\"result__a\" href=\"([^\"]+)\"[^>]*>(.*?)</a>"
                            + ".*?<a class=\"result__snippet\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL);
            // Fallback pattern: just the link title
            Pattern linkOnly = Pattern.compile(
                    "<a class=\"result__a\" href=\"([^\"]+)\"[^>]*>(.*?)</a>");

            Matcher m = resultWithSnippet.matcher(html);
            int count = 0;
            Set<String> seenUrls = new java.util.LinkedHashSet<>();

            while (m.find() && count < maxResults) {
                String url = m.group(1);
                String title = stripHtml(m.group(2)).trim();
                String snippet = stripHtml(m.group(3)).trim();

                if (!seenUrls.contains(url) && !title.isEmpty() && !title.contains("More results")) {
                    seenUrls.add(url);
                    sb.append(count + 1).append(". ").append(title).append("\n");
                    if (!snippet.isEmpty()) {
                        sb.append("   ").append(snippet).append("\n");
                    }
                    sb.append("   URL: ").append(url).append("\n\n");
                    count++;
                }
            }

            // Fallback: if no results with snippets, try link-only pattern
            if (count == 0) {
                Matcher lm = linkOnly.matcher(html);
                while (lm.find() && count < maxResults) {
                    String url = lm.group(1);
                    String title = stripHtml(lm.group(2)).trim();
                    if (!seenUrls.contains(url) && !title.isEmpty()
                            && !title.contains("More results") && !title.contains("More »")) {
                        seenUrls.add(url);
                        sb.append(count + 1).append(". ").append(title).append("\n");
                        sb.append("   URL: ").append(url).append("\n\n");
                        count++;
                    }
                }
            }

            if (count == 0) {
                return "No search results found for: \"" + query + "\"";
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Error performing web search for '" + query + "': " + e.getMessage();
        }
    }

    private static String fetchRaw(String url) {
        HttpURLConnection conn = null;
        try {
            URL target = URI.create(url).toURL();
            conn = (HttpURLConnection) target.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; LLM-Tools/1.0)");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int httpCode = conn.getResponseCode();
            if (httpCode != 200) {
                return "Error: HTTP " + httpCode + " for URL: " + url;
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    body.append(line).append("\n");
                }
            }
            return body.toString();
        } catch (Exception e) {
            return "Error fetching webpage '" + url + "': " + e.getMessage();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    // ===== SHELL / BASH TOOL =====

    /**
     * Execute a shell command and return the combined stdout and stderr output.
     * Automatically selects the right shell for the operating system:
     * bash/sh on Linux/macOS, cmd.exe on Windows.
     *
     * @param command the shell command to execute
     * @param cwd     optional working directory for command execution
     * @return stdout and stderr output, or error message
     */
    @LLMTool(name = "run_bash",
            description = "Execute a shell command and return the output. " +
                    "Uses bash on Linux/macOS and cmd.exe on Windows. " +
                    "Captures both stdout and stderr.")
    public static String runBash(
            @ToolParam(name = "command", description = "The shell command to execute") String command,
            @ToolParam(name = "cwd",
                    description = "Optional working directory for the command", required = false) String cwd) {
        try {
            String osName = System.getProperty("os.name", "").toLowerCase();
            boolean isWindows = osName.contains("win");

            // Build the process with the correct shell for the platform
            ProcessBuilder pb;
            if (isWindows) {
                // Windows: use cmd.exe with /c flag
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                // Linux/macOS: use /bin/sh with -c flag
                pb = new ProcessBuilder("/bin/sh", "-c", command);
            }

            // Don't merge stderr — we capture it separately
            pb.redirectErrorStream(false);

            // Set optional working directory
            if (cwd != null && !cwd.trim().isEmpty()) {
                pb.directory(new File(cwd));
            }

            // Set TERM to prevent interactive prompts
            pb.environment().put("TERM", "dumb");

            // Start the process
            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            // Read stdout
            try (BufferedReader stdout = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = stdout.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Read stderr separately
            try (BufferedReader stderr = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = stderr.readLine()) != null) {
                    output.append("[stderr] ").append(line).append("\n");
                }
            }

            // Wait for process to complete and get exit code
            int exitCode = process.waitFor();

            // If exit code is non-zero and no output, explain the failure
            if (exitCode != 0 && output.length() == 0) {
                output.append("(command exited with code ").append(exitCode).append(")");
            }

            // Truncate very large output to prevent memory issues
            String result = output.toString();
            if (result.length() > 10000) {
                result = result.substring(0, 10000)
                        + "\n... (output truncated at 10000 characters)";
            }

            return result;
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    // ===== AUTO-REGISTRATION HELPERS =====
    // These static methods register tool groups with a ToolRegistry.

    /**
     * Register all 13 built-in system tools with a registry.
     */
    public static void registerAll(ToolRegistry registry) {
        registry.registerAll(new SystemTools());
    }

    /**
     * Register only the file operation tools: read_file, write_file, create_file,
     * append_file, delete_file, path_exists, file_info.
     */
    public static void registerFileTools(ToolRegistry registry) {
        registry.registerAll(new FileTools());
    }

    /**
     * Register only the web tools: web_search, fetch_webpage.
     */
    public static void registerWebTools(ToolRegistry registry) {
        registry.registerAll(new WebTools());
    }

    /**
     * Register only the shell tool: run_bash.
     */
    public static void registerShellTools(ToolRegistry registry) {
        registry.registerAll(new ShellTools());
    }

    // ===== SELECTIVE REGISTRATION SUB-CLASSES =====
    // These inner classes group tools by category, allowing selective registration.
    // Each method is annotated with @LLMTool so ToolRegistry can auto-discover them.
    // They delegate to the static methods above.

    /**
     * File and directory operation tools.
     * Register with: SystemTools.registerFileTools(registry);
     */
    public static class FileTools {

        @LLMTool(name = "read_file", description = "Read a file's contents.")
        public String readFile(
                @ToolParam(name = "path") String path) {
            return SystemTools.readFile(path);
        }

        @LLMTool(name = "write_file", description = "Write content to a file.")
        public String writeFile(
                @ToolParam(name = "path") String path,
                @ToolParam(name = "content") String content) {
            return SystemTools.writeFile(path, content);
        }

        @LLMTool(name = "create_file", description = "Create an empty file.")
        public String createFile(
                @ToolParam(name = "path") String path) {
            return SystemTools.createFile(path);
        }

        @LLMTool(name = "append_file", description = "Append content to a file.")
        public String appendFile(
                @ToolParam(name = "path") String path,
                @ToolParam(name = "content") String content) {
            return SystemTools.appendFile(path, content);
        }

        @LLMTool(name = "delete_file", description = "Delete a file.")
        public String deleteFile(
                @ToolParam(name = "path") String path) {
            return SystemTools.deleteFile(path);
        }

        @LLMTool(name = "list_dir", description = "List directory contents.")
        public String listDir(
                @ToolParam(name = "path") String path,
                @ToolParam(name = "recursive", required = false) String recursive) {
            return SystemTools.listDir(path, recursive);
        }

        @LLMTool(name = "find_files", description = "Find files by glob pattern.")
        public String findFiles(
                @ToolParam(name = "path") String path,
                @ToolParam(name = "pattern") String pattern) {
            return SystemTools.findFiles(path, pattern);
        }

        @LLMTool(name = "grep", description = "Search text within files.")
        public String grep(
                @ToolParam(name = "path") String path,
                @ToolParam(name = "pattern") String pattern,
                @ToolParam(name = "file_pattern", required = false) String filePattern) {
            return SystemTools.grep(path, pattern, filePattern);
        }

        @LLMTool(name = "path_exists", description = "Check if path exists.")
        public String pathExists(
                @ToolParam(name = "path") String path) {
            return SystemTools.pathExists(path);
        }

        @LLMTool(name = "file_info", description = "Get file metadata.")
        public String fileInfo(
                @ToolParam(name = "path") String path) {
            return SystemTools.fileInfo(path);
        }
    }

    /**
     * Web search and fetch tools.
     * Register with: SystemTools.registerWebTools(registry);
     */
    public static class WebTools {

        @LLMTool(name = "fetch_webpage", description = "Fetch webpage content.")
        public String fetchWebpage(
                @ToolParam(name = "url") String url,
                @ToolParam(name = "max_length", required = false) Integer maxLength) {
            return SystemTools.fetchWebpage(url, maxLength);
        }

        @LLMTool(name = "web_search", description = "Search the web.")
        public String webSearch(
                @ToolParam(name = "query") String query,
                @ToolParam(name = "limit", required = false) Integer limit) {
            return SystemTools.webSearch(query, limit);
        }
    }

    /**
     * Shell execution tool.
     * Register with: SystemTools.registerShellTools(registry);
     */
    public static class ShellTools {

        @LLMTool(name = "run_bash", description = "Run a shell command.")
        public String runBash(
                @ToolParam(name = "command") String command,
                @ToolParam(name = "cwd", required = false) String cwd) {
            return SystemTools.runBash(command, cwd);
        }
    }
}
