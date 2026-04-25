package demo;

import in.simpletools.llm.framework.client.LLMClient;

final class ContextWindowExample {
    private ContextWindowExample() {}

    static void run() {
        ExampleSupport.printSection("Context Window");
        LLMClient client = ExampleSupport.ollamaClient().withContextWindow(32000);

        var projected = client.getProjectedContextInfo("""
            Build a long travel plan with preferences, constraints, budgets,
            sightseeing options, and restaurant recommendations.
            """);

        System.out.println("Current:   " + client.getContextInfo().summary());
        System.out.println("Projected: " + projected.summary());

        String reply = client.chat("Store this preference: I prefer train travel over flights.");
        System.out.println("Reply: " + reply);
        System.out.println("Updated:   " + client.getContextInfo().summary());
    }
}
