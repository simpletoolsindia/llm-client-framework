package demo;

import java.util.Map;

public class ShowcaseApp {
    private static final Map<String, Runnable> EXAMPLES = Map.of(
        "basic-chat", BasicChatExample::run,
        "context-window", ContextWindowExample::run,
        "auto-compaction", AutoCompactionExample::run,
        "verbose-logging", VerboseLoggingExample::run,
        "tool-calling", ToolCallingExample::run,
        "annotation-tools", AnnotationToolExample::run,
        "async-streaming", AsyncStreamingExample::run
    );

    public static void main(String[] args) {
        String example = args.length > 0 ? args[0] : "basic-chat";

        if ("list".equalsIgnoreCase(example)) {
            System.out.println("Available examples:");
            EXAMPLES.keySet().stream().sorted().forEach(name -> System.out.println("- " + name));
            return;
        }

        Runnable runnable = EXAMPLES.get(example);
        if (runnable == null) {
            System.err.println("Unknown example: " + example);
            System.err.println("Run with `list` to see available examples.");
            System.exit(1);
        }

        runnable.run();
    }
}
