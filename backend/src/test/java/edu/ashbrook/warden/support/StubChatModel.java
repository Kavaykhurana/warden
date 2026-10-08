package edu.ashbrook.warden.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/** A programmable model: tests script the responses, so Gemini is never called. */
public class StubChatModel implements ChatModel {

    private volatile Function<Prompt, ChatResponse> behaviour = p -> {
        throw new IllegalStateException("No model in tests");
    };
    private final AtomicInteger calls = new AtomicInteger();
    private final List<Prompt> prompts = new ArrayList<>();

    @Override
    public synchronized ChatResponse call(Prompt prompt) {
        calls.incrementAndGet();
        prompts.add(prompt);
        return behaviour.apply(prompt);
    }

    public void respond(Function<Prompt, ChatResponse> behaviour) {
        this.behaviour = behaviour;
        calls.set(0);
        synchronized (this) {
            prompts.clear();
        }
    }

    public int calls() {
        return calls.get();
    }

    public synchronized List<Prompt> prompts() {
        return List.copyOf(prompts);
    }

    public static ChatResponse text(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** A response asking the client to run one tool with the given JSON arguments. */
    public static ChatResponse toolCall(String tool, String jsonArguments) {
        AssistantMessage message = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + tool, "function", tool, jsonArguments)))
                .build();
        return new ChatResponse(List.of(new Generation(message)));
    }
}
