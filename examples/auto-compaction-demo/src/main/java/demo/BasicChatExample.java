package demo;

import in.simpletools.llm.framework.client.LLMClient;

final class BasicChatExample {
    private BasicChatExample() {}

    static void run() {
        ExampleSupport.printSection("Basic Chat");
        LLMClient client = ExampleSupport.ollamaClient();

        String reply = client.chat("Explain recursion in a short and friendly way.");
        System.out.println("Reply: " + reply);
        System.out.println("Context: " + client.getContextInfo().summary());
    }
}
