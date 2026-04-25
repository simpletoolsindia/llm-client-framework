package in.simpletools.llm.framework.example;

import in.simpletools.llm.framework.client.*;
import in.simpletools.llm.framework.tool.*;
import java.util.*;

/**
 * Comprehensive demo of the LLM Client Framework
 * Tests: basic chat, streaming, function calling, history, system prompts
 */
public class OllamaDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("  LLM Client Framework - Ollama Demo");
        System.out.println("=".repeat(60));
        System.out.println();

        // Create client
        LLMClient client = LLMClientFactory.ollama("gemma4:latest");

        // Test 1: Basic Chat
        testBasicChat(client);

        // Test 2: System Prompt
        testSystemPrompt(client);

        // Test 3: Streaming
        testStreaming(client);

        // Test 4: Function Calling (Calculator)
        testFunctionCalling(client);

        // Test 5: Conversation History
        testConversationHistory(client);

        // Test 6: Multiple Turns
        testMultipleTurns(client);

        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("  All tests completed!");
        System.out.println("=".repeat(60));
    }

    private static void testBasicChat(LLMClient client) {
        System.out.println("TEST 1: Basic Chat");
        System.out.println("-".repeat(40));

        String response = client.chat("What is Java? Keep it brief.");
        System.out.println("Response: " + response);
        System.out.println();
        client.clearHistory();
    }

    private static void testSystemPrompt(LLMClient client) {
        System.out.println("TEST 2: System Prompt");
        System.out.println("-".repeat(40));

        String response = client.chat("Explain recursion", Map.of(
            "system", "You are a friendly computer science professor. Use simple words."
        ));
        System.out.println("Response: " + response);
        System.out.println();
        client.clearHistory();
    }

    private static void testStreaming(LLMClient client) {
        System.out.println("TEST 3: Streaming Response");
        System.out.println("-".repeat(40));
        System.out.print("Streaming: ");

        client.streamChat("Count to 5 with emojis", chunk -> System.out.print(chunk));
        System.out.println();
        System.out.println();
        client.clearHistory();
    }

    private static void testFunctionCalling(LLMClient client) {
        System.out.println("TEST 4: Function Calling (Calculator)");
        System.out.println("-".repeat(40));

        // Register calculator tool
        client.tool(
            "calculate",
            "Evaluates a mathematical expression and returns the result",
            args -> {
                String expr = (String) args.get("expression");
                try {
                    return new javax.script.ScriptEngineManager()
                        .getEngineByName("JavaScript")
                        .eval(expr);
                } catch (Exception e) {
                    return "Error: " + e.getMessage();
                }
            },
            Map.of(
                "expression", new ToolRegistry.ParamInfo(
                    "expression",
                    "The math expression to evaluate",
                    true,
                    String.class
                )
            )
        );

        // Ask a question that requires math - be explicit about using the tool
        String response = client.chat(
            "Calculate the result of (125 + 375) / 25. Use the calculate tool."
        );
        System.out.println("Response: " + response);
        System.out.println();
        client.clearHistory();
    }

    private static void testConversationHistory(LLMClient client) {
        System.out.println("TEST 5: Conversation History");
        System.out.println("-".repeat(40));

        // Add some messages
        client.chat("My favorite color is blue.");
        client.chat("What color is my favorite?");

        System.out.println("History size: " + client.getHistory().size() + " messages");
        System.out.println("Last assistant message: " +
            client.getHistory().getMessages().get(client.getHistory().size() - 1).content());

        client.clearHistory();
        System.out.println("After clear - History size: " + client.getHistory().size());
        System.out.println();
    }

    private static void testMultipleTurns(LLMClient client) {
        System.out.println("TEST 6: Multi-Turn Conversation");
        System.out.println("-".repeat(40));

        // Turn 1
        String r1 = client.chat("I am learning Java programming.");
        System.out.println("Turn 1: " + r1);

        // Turn 2
        String r2 = client.chat("What am I learning?");
        System.out.println("Turn 2: " + r2);

        // Turn 3
        String r3 = client.chat("What programming language am I learning?");
        System.out.println("Turn 3: " + r3);

        System.out.println("Total messages in history: " + client.getHistory().size());
        System.out.println();
    }
}
