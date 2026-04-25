package demo;

import in.simpletools.llm.framework.client.LLMClient;

final class AutoCompactionExample {
    private AutoCompactionExample() {}

    static void run() {
        ExampleSupport.printSection("Auto Compaction");
        LLMClient client = ExampleSupport.ollamaClient()
            .withContextWindow(350)
            .withAutoCompaction(55.0, 30.0, 4);

        for (int i = 1; i <= 6; i++) {
            String reply = client.chat("""
                I am building an AI travel planner.
                Keep these durable user preferences:
                - vegetarian food
                - train travel preferred
                - calm neighborhoods
                - medium budget
                Add one planning note for turn %d with useful trip context.
                """.formatted(i));

            System.out.println("Turn " + i + " reply: " + reply);
            System.out.println("Context: " + client.getContextInfo().summary());
            System.out.println("Rolling summary: " + client.getCompactedContextSummary());
            System.out.println("---");
        }
    }
}
