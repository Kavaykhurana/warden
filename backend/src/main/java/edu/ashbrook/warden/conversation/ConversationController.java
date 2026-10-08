package edu.ashbrook.warden.conversation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import edu.ashbrook.warden.ai.ChatService;
import edu.ashbrook.warden.common.ConflictException;
import edu.ashbrook.warden.conversation.ConversationDtos.ChatTurn;
import edu.ashbrook.warden.conversation.ConversationDtos.ConversationDto;
import edu.ashbrook.warden.conversation.ConversationDtos.MessageDto;
import edu.ashbrook.warden.conversation.ConversationDtos.SendRequest;
import edu.ashbrook.warden.conversation.ConversationDtos.TitleRequest;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversations;
    private final ChatService chat;

    public ConversationController(ConversationService conversations, ChatService chat) {
        this.conversations = conversations;
        this.chat = chat;
    }

    @GetMapping
    List<ConversationDto> list() {
        return conversations.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ConversationDto create(@RequestBody(required = false) TitleRequest body) {
        return conversations.create(body == null ? null : body.title());
    }

    @PatchMapping("/{id}")
    ConversationDto rename(@PathVariable UUID id, @RequestBody TitleRequest body) {
        return conversations.rename(id, body.title());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        conversations.requireOwned(id);
        // Deleting mid-reply would make the turn unsavable, hiding any action a tool already took.
        if (chat.isReplying(id)) {
            throw new ConflictException("A reply is still being written. Delete the conversation once it finishes.");
        }
        conversations.delete(id);
    }

    @GetMapping("/{id}/messages")
    List<MessageDto> messages(@PathVariable UUID id) {
        return conversations.messages(id);
    }

    @PostMapping("/{id}/messages")
    ChatTurn send(@PathVariable UUID id, @RequestBody SendRequest body) {
        return chat.send(id, body.content());
    }
}
