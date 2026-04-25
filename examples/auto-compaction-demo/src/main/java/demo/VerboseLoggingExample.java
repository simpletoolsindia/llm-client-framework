package demo;

import in.simpletools.llm.framework.client.LLMClient;
import in.simpletools.llm.framework.utils.SimpleLogger;

final class VerboseLoggingExample {
    private VerboseLoggingExample() {}

    static void run() {
        ExampleSupport.printSection("Verbose Logging");
        LLMClient client = ExampleSupport.ollamaClient()
            .withVerboseLogging(SimpleLogger.Level.DEBUG)
            .withAutoCompaction(70.0, 40.0, 3);

        String reply = client.chat("""
            Remember these user preferences:
            - vegetarian meals
            - quiet stays
            - train travel preferred
            Then suggest a short weekend itinerary.
            """);

        System.out.println("Reply: " + reply);
        System.out.println("Context: " + client.getContextInfo().summary());
        System.out.println("Verbose logging was enabled for this run.");
    }
}
