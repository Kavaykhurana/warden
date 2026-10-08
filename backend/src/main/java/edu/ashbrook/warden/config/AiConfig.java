package edu.ashbrook.warden.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
