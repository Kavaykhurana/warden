package edu.ashbrook.warden.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

/**
 * One {@link ChatClient} over whichever {@link ChatModel} is configured (Gemini in production, a stub in tests).
 * Model, temperature and output limit come from {@code spring.ai.google.genai.chat.*}. No chat memory advisor: the
 * {@code messages} table is the memory.
 */
@Configuration(proxyBeanMethods = false)
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    /**
     * Replaces Spring AI's default Gemini client only to switch off the SDK's own retries: otherwise a 429 is retried
     * inside the SDK until the 45-second turn budget runs out, instead of failing fast through {@code spring.ai.retry}.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
    Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey) {
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().retryOptions(HttpRetryOptions.builder().attempts(1)).build())
                .build();
    }
}
