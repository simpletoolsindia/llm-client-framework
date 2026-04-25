package in.simpletools.llm.framework.history;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TokenTrackerTest {
    @Test
    void detectsTaggedOllamaModelContextWindow() {
        assertEquals(32000L, TokenTracker.detectLimitForModel("gemma4:latest"));
    }

    @Test
    void detectsModernOpenAIContextWindow() {
        assertEquals(1047576L, TokenTracker.detectLimitForModel("gpt-4.1-mini"));
    }

    @Test
    void fallsBackForUnknownModels() {
        assertEquals(4096L, TokenTracker.detectLimitForModel("custom-local-model"));
    }
}
