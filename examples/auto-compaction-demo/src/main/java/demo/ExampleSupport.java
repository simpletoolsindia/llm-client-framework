package demo;

import in.simpletools.llm.framework.client.LLMClient;

final class ExampleSupport {
    private ExampleSupport() {}

    static String model() {
        return System.getenv().getOrDefault("OLLAMA_MODEL", "gemma4:latest");
    }

    static LLMClient ollamaClient() {
        return LLMClient.ollama(model());
    }

    static void printSection(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
