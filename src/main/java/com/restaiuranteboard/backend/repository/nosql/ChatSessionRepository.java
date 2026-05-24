package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.ChatSession;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends MongoRepository<ChatSession, String> {
    Optional<ChatSession> findFirstByUserIdAndChatTypeAndClosedFalseOrderByUpdatedAtDesc(
            String userId,
            String chatType
    );

    List<ChatSession> findByUserIdAndChatTypeOrderByUpdatedAtDesc(String userId, String chatType);
}
