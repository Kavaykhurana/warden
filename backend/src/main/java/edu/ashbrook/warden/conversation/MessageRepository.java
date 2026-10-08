package edu.ashbrook.warden.conversation;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByIdAsc(UUID conversationId);

    List<Message> findByConversationIdOrderByIdDesc(UUID conversationId, Limit limit);
}
