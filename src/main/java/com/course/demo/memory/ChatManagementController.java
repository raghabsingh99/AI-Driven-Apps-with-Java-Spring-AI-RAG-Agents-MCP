package com.course.demo.memory;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Management surface over the JDBC-backed chat memory used by {@link PersistentChatMemoryDemo}.
 * This project has no User entity or auth, so "users" are represented by conversationId
 * (the HttpSession id by default, or a caller-supplied id) - the same identifier the chat
 * endpoint keys conversations on.
 */
@RestController
@RequestMapping(path = "/api/chat-management", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ChatManagementController {

    private final ChatMemoryRepository chatMemoryRepository;

    @GetMapping("/session")
    public SessionInfo session(HttpSession session) {
        return SessionInfo.builder()
                .sessionId(session.getId())
                .creationTime(Instant.ofEpochMilli(session.getCreationTime()))
                .lastAccessedTime(Instant.ofEpochMilli(session.getLastAccessedTime()))
                .maxInactiveIntervalSeconds(session.getMaxInactiveInterval())
                .build();
    }

    @DeleteMapping("/session")
    public String invalidateSession(HttpSession session) {
        String sessionId = session.getId();
        session.invalidate();
        return "Invalidated session '" + sessionId + "'.";
    }

    @GetMapping("/conversations")
    public List<String> conversations() {
        return chatMemoryRepository.findConversationIds();
    }

    @GetMapping("/conversations/{conversationId}")
    public List<Map<String, String>> conversation(@PathVariable String conversationId) {
        return chatMemoryRepository.findByConversationId(conversationId).stream()
                .map(message -> Map.of("type", message.getMessageType().name(), "content", message.getText()))
                .toList();
    }

    @DeleteMapping("/conversations/{conversationId}")
    public String deleteConversation(@PathVariable String conversationId) {
        chatMemoryRepository.deleteByConversationId(conversationId);
        return "Deleted conversation '" + conversationId + "'.";
    }
}
