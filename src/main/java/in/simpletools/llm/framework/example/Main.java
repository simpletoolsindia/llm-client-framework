package in.simpletools.llm.framework.example;

import in.simpletools.llm.framework.client.*;
import in.simpletools.llm.framework.tool.*;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("=== LLM Client Framework Demo ===\n");

        // Example 1: Ollama
        System.out.println("--- 1. Ollama (Local) ---");
        LLMClient ollama = LLMClientFactory.ollama("gemma4:latest");
        try {
            String response = ollama.chat("What is Java? Reply in one sentence.");
            System.out.println("Response: " + response);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        // Example 2: With Tools
        System.out.println("\n--- 2. With Calculator Tool ---");
        LLMClient clientWithTools = LLMClientFactory.ollama("gemma4:latest");
        clientWithTools.registerTool("calculate", "Evaluate math expression",
            argsMap -> {
                String expr = (String) argsMap.get("expression");
                try {
                    return new javax.script.ScriptEngineManager().getEngineByName("JavaScript").eval(expr);
                } catch (Exception e) { return "Error: " + e.getMessage(); }
            },
            java.util.Map.of("expression", new ToolRegistry.ParamInfo("expression", "Math expression", true, String.class))
        );

        try {
            String response = clientWithTools.chat("What is 25 * 4 + 10?");
            System.out.println("Response: " + response);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        // Example 3: Streaming
        System.out.println("\n--- 3. Streaming ---");
        System.out.print("Streaming: ");
        try {
            ollama.streamChat("Count to 3", chunk -> System.out.print(chunk));
            System.out.println();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        System.out.println("\n=== Done ===");
    }
}