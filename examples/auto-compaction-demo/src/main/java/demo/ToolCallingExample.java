package demo;

import in.simpletools.llm.framework.client.LLMClient;
import in.simpletools.llm.framework.tool.ToolRegistry;
import java.util.Map;

final class ToolCallingExample {
    private ToolCallingExample() {}

    static void run() {
        ExampleSupport.printSection("Lambda Tool Calling");
        LLMClient client = ExampleSupport.ollamaClient();

        client.tool(
            "trip_budget",
            "Return a simple total travel budget estimate",
            args -> {
                int hotel = Integer.parseInt(args.get("hotel").toString());
                int food = Integer.parseInt(args.get("food").toString());
                int transport = Integer.parseInt(args.get("transport").toString());
                return Map.of(
                    "total", hotel + food + transport,
                    "currency", "INR"
                );
            },
            Map.of(
                "hotel", new ToolRegistry.ParamInfo("hotel", "Hotel budget", true, Integer.class),
                "food", new ToolRegistry.ParamInfo("food", "Food budget", true, Integer.class),
                "transport", new ToolRegistry.ParamInfo("transport", "Transport budget", true, Integer.class)
            )
        );

        String reply = client.chat("""
            Use the trip_budget tool for hotel=4000, food=1500, transport=1200.
            Then explain the total in one line.
            """);
        System.out.println("Reply: " + reply);
    }
}
