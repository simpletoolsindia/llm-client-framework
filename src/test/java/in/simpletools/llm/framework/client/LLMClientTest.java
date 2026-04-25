package in.simpletools.llm.framework.client;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simpletools.llm.framework.adapter.ProviderAdapter;
import in.simpletools.llm.framework.config.ClientConfig;
import in.simpletools.llm.framework.config.Provider;
import in.simpletools.llm.framework.model.LLMRequest;
import in.simpletools.llm.framework.model.LLMResponse;
import in.simpletools.llm.framework.model.Message;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import in.simpletools.llm.framework.utils.SimpleLogger;

class LLMClientTest {
    @Test
    void autoCompactionCreatesRollingSummary() {
        LLMClient client = newClient().withContextWindow(120).withAutoCompaction(30.0, 15.0, 2);

        client.chat(longUserMessage("vegetarian food and train travel"));
        client.chat(longUserMessage("quiet hotels and medium budget"));

        assertNotNull(client.getCompactedContextSummary());
        assertTrue(client.getHistory().getMessages().stream()
            .anyMatch(message -> message.getRole() == Message.Role.system
                && message.getContent() != null
                && message.getContent().contains("Conversation summary for continued context:")));
    }

    @Test
    void withRetryPreservesAutoCompactionSettings() {
        LLMClient client = newClient()
            .withContextWindow(120)
            .withAutoCompaction(30.0, 15.0, 2)
            .withRetry(5);

        client.chat(longUserMessage("first large context block"));
        client.chat(longUserMessage("second large context block"));

        assertNotNull(client.getCompactedContextSummary());
    }

    @Test
    void switchingToMemoryHistoryResetsCompactedState() {
        LLMClient client = newClient().withContextWindow(120).withAutoCompaction(30.0, 15.0, 2);

        client.chat(longUserMessage("persistent preferences one"));
        client.chat(longUserMessage("persistent preferences two"));
        assertNotNull(client.getCompactedContextSummary());

        client.withMemoryHistory();

        assertNull(client.getCompactedContextSummary());
        assertTrue(client.getHistory().getMessages().isEmpty());
        assertTrue(client.getContextInfo().usedTokens() == 0);
    }

    @Test
    void verboseLoggingCanBeEnabled() {
        LLMClient client = newClient().withVerboseLogging(SimpleLogger.Level.DEBUG);

        assertTrue(client.getLogger().isVerbose());
        assertTrue(client.getLogger().getLevel() == SimpleLogger.Level.DEBUG);
    }

    private LLMClient newClient() {
        ClientConfig config = ClientConfig.of(Provider.OPENAI)
            .model("gemma4:latest")
            .apiKey("test-key");

        return LLMClient.builder()
            .config(config)
            .adapter(new FakeProviderAdapter())
            .build();
    }

    private String longUserMessage(String topic) {
        return ("Remember this user preference forever: " + topic + ". ").repeat(12);
    }

    private static final class FakeProviderAdapter implements ProviderAdapter {
        @Override
        public LLMResponse chat(LLMRequest request) {
            List<Message> messages = request.getMessages();
            String lastUserMessage = messages.stream()
                .filter(message -> message.getRole() == Message.Role.user)
                .map(Message::getContent)
                .reduce((first, second) -> second)
                .orElse("");

            boolean summaryRequest = messages.stream()
                .filter(message -> message.getRole() == Message.Role.system)
                .map(Message::getContent)
                .filter(content -> content != null && content.contains("compressing a conversation"))
                .findFirst()
                .isPresent();

            String content = summaryRequest
                ? "Summary: preserve user travel preferences, constraints, and unresolved work."
                : "Reply: " + lastUserMessage.substring(0, Math.min(lastUserMessage.length(), 40));

            LLMResponse response = new LLMResponse();
            response.setModel("gemma4:latest");
            response.setMessage(new Message(Message.Role.assistant, content));
            return response;
        }

        @Override
        public void streamChat(LLMRequest request, Consumer<String> onChunk) {
            onChunk.accept(chat(request).getContentOrEmpty());
        }

        @Override
        public String generate(String prompt) {
            return "Generated: " + prompt;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public Map<String, String> getHeaders() {
            return Map.of();
        }
    }
}
