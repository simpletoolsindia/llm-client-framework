package demo;

import in.simpletools.llm.framework.client.LLMClient;
import in.simpletools.llm.framework.tool.LLMTool;
import in.simpletools.llm.framework.tool.ToolParam;

final class AnnotationToolExample {
    private AnnotationToolExample() {}

    static void run() {
        ExampleSupport.printSection("Annotation Tools");
        LLMClient client = ExampleSupport.ollamaClient();
        client.registerTools(new TravelTools());

        String reply = client.chat("""
            Use the city_tip tool for city=Jaipur and season=winter.
            Keep the answer concise.
            """);
        System.out.println("Reply: " + reply);
    }

    static final class TravelTools {
        @LLMTool(name = "city_tip", description = "Return a short city-specific travel tip")
        public String cityTip(
            @ToolParam(name = "city", description = "City name") String city,
            @ToolParam(name = "season", description = "Travel season") String season
        ) {
            return "Travel tip for " + city + " in " + season + ": start early, stay hydrated, and pre-book major attractions.";
        }
    }
}
