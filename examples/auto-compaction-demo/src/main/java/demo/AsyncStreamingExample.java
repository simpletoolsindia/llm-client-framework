package demo;

import in.simpletools.llm.framework.client.LLMClient;

final class AsyncStreamingExample {
    private AsyncStreamingExample() {}

    static void run() {
        ExampleSupport.printSection("Async And Streaming");
        LLMClient client = ExampleSupport.ollamaClient();

        String asyncReply = client.chatAsync("Write a two-line trip summary for Munnar.").join();
        System.out.println("Async reply: " + asyncReply);

        System.out.println("Streaming reply:");
        client.streamChat(
            "Count from 1 to 5 with short travel-themed phrases.",
            token -> System.out.print(token),
            error -> System.err.println("Stream error: " + error)
        );
        System.out.println();
    }
}
