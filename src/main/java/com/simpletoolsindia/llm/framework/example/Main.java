package com.simpletoolsindia.llm.framework.example;

import com.simpletoolsindia.llm.framework.client.*;
import com.simpletoolsindia.llm.framework.config.*;
import com.simpletoolsindia.llm.framework.tool.*;
import com.simpletoolsindia.llm.framework.history.ConversationHistory;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("=== LLM Client Framework Demo ===\n");

        // Example 1: Ollama (Local)
        System.out.println("--- 1. Ollama (Local) ---");
        LLMClient ollama = LLMClientFactory.ollama("gemma4:latest");
        System.out.println("Client created: " + (ollama != null));
        try {
            String response = ollama.chat("What is Java? Reply in one sentence.");
            System.out.println("Response: " + response);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        // Example 2: OpenAI
        System.out.println("\n--- 2. OpenAI ---");
        String openAIKey = System.getenv("OPENAI_API_KEY");
        if (openAIKey != null) {
            LLMClient openai = LLMClientFactory.openAI("gpt-4o", openAIKey);
            System.out.println("Client created");
        } else {
            System.out.println("OPENAI_API_KEY not set - skipping");
        }

        // Example 3: Claude
        System.out.println("\n--- 3. Claude ---");
        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
        if (anthropicKey != null) {
            LLMClient claude = LLMClientFactory.claude("claude-sonnet-4-20250514", anthropicKey);
            System.out.println("Client created");
        } else {
            System.out.println("ANTHROPIC_API_KEY not set - skipping");
        }

        // Example 4: DeepSeek
        System.out.println("\n--- 4. DeepSeek ---");
        String deepseekKey = System.getenv("DEEPSEEK_API_KEY");
        if (deepseekKey != null) {
            LLMClient deepseek = LLMClientFactory.deepSeek("deepseek-chat", deepseekKey);
            System.out.println("Client created");
        } else {
            System.out.println("DEEPSEEK_API_KEY not set - skipping");
        }

        // Example 5: LM Studio
        System.out.println("\n--- 5. LM Studio ---");
        LLMClient lmStudio = LLMClientFactory.lmStudio("local-model");
        System.out.println("Client created");

        // Example 6: NVIDIA
        System.out.println("\n--- 6. NVIDIA ---");
        String nvidiaKey = System.getenv("NVIDIA_API_KEY");
        if (nvidiaKey != null) {
            LLMClient nvidia = LLMClientFactory.nvidia("meta/llama3-70b-instruct", nvidiaKey);
            System.out.println("Client created");
        } else {
            System.out.println("NVIDIA_API_KEY not set - skipping");
        }

        // Example 7: With Tools
        System.out.println("\n--- 7. With Tools ---");
        LLMClient clientWithTools = LLMClientFactory.ollama("gemma4:latest");

        // Register a simple calculator tool using a lambda
        clientWithTools.registerTool(
            "calculate",
            "Evaluate a mathematical expression",
            argsMap -> {
                String expr = (String) argsMap.get("expression");
                try {
                    return eval(expr);
                } catch (Exception e) {
                    return "Error: " + e.getMessage();
                }
            },
            java.util.Map.of("expression",
                new ToolRegistry.ParamInfo("expression", "Math expression like 2+2", true, String.class)
            )
        );

        try {
            String response = clientWithTools.chat("What is 25 * 4 + 10?");
            System.out.println("Response: " + response);
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        // Example 8: Streaming
        System.out.println("\n--- 8. Streaming ---");
        System.out.print("Streaming response: ");
        try {
            ollama.streamChat("Count to 5", chunk -> System.out.print(chunk));
            System.out.println();
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }

        // Example 9: History
        System.out.println("\n--- 9. History ---");
        ConversationHistory history = ollama.getHistory();
        System.out.println("Messages in history: " + history.size());

        System.out.println("\n=== Done ===");
    }

    static double eval(String expr) {
        try {
            return (double) new javax.script.ScriptEngineManager()
                .getEngineByName("JavaScript")
                .eval(expr);
        } catch (Exception e) {
            return Double.NaN;
        }
    }
}