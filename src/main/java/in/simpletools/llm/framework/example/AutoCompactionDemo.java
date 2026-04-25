package in.simpletools.llm.framework.example;

import in.simpletools.llm.framework.client.LLMClient;

public class AutoCompactionDemo {
    public static void main(String[] args) {
        LLMClient client = LLMClient.ollama("gemma4:latest")
            .withAutoCompaction(80.0, 50.0, 4);

        String reply = client.chat("""
            Keep these user preferences in memory:
            - vegetarian meals
            - train travel preferred
            - budget under control
            - avoid noisy places
            Then suggest a weekend trip.
            """);

        System.out.println(reply);
        System.out.println(client.getContextInfo().summary());
        System.out.println("Compacted summary: " + client.getCompactedContextSummary());
    }
}
