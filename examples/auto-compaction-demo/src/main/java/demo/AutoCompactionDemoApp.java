package demo;

import in.simpletools.llm.framework.client.LLMClient;

public class AutoCompactionDemoApp {
    public static void main(String[] args) {
        LLMClient client = LLMClient.ollama("gemma4:latest")
            .withAutoCompaction(80.0, 50.0, 4);

        for (int i = 1; i <= 8; i++) {
            String reply = client.chat("""
                I am building an AI travel planner.
                Remember these preferences:
                - user likes trains more than flights
                - prefers vegetarian food
                - budget is medium
                - wants calm places
                Add one extra planning note for turn %d.
                """.formatted(i));

            System.out.println("Turn " + i + " reply: " + reply);
            System.out.println("Context: " + client.getContextInfo().summary());
            System.out.println("Compacted summary: " + client.getCompactedContextSummary());
            System.out.println("-----");
        }
    }
}
