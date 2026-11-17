package com.course.demo.memory;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Same conversational-memory demo as {@link ChatMemoryDemo}, but backed by the
 * JDBC {@link ChatMemoryRepository} auto-configured against the MySQL
 * datasource (see application.properties), instead of an in-memory one. Because the
 * conversation rows live in the database, history survives app restarts.
 */
@RestController
@RequestMapping(path = "/api/persistent-chat-memory-demo", produces = MediaType.TEXT_PLAIN_VALUE)
@RequiredArgsConstructor
public class PersistentChatMemoryDemo {

    private final ChatClient.Builder chatClientBuilder;
    private final ChatMemoryRepository chatMemoryRepository;

    @GetMapping(path = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatMemoryResponse chat(HttpSession session,
                                    @RequestParam(required = false) String conversationId,
                                    @RequestParam(defaultValue = "What is Spring AI?") String prompt) {
        String resolvedConversationId = StringUtils.hasText(conversationId)
                ? conversationId
                : session.getId();

        String content = chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .advisors(advisorSpec -> advisorSpec
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory()).build())
                        .param(ChatMemory.CONVERSATION_ID, resolvedConversationId))
                .call()
                .content();

        return ChatMemoryResponse.builder()
                .conversationId(resolvedConversationId)
                .content(content)
                .build();
    }

    @GetMapping("/conversation-id")
    public String conversationId(HttpSession session) {
        return session.getId();
    }

    @GetMapping(path = "/conversation-ids", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> conversationIds() {
        return chatMemoryRepository.findConversationIds();
    }

    @GetMapping("/history")
    public String history(@RequestParam(defaultValue = "demo-conversation") String conversationId) {
        List<Message> messages = chatMemory().get(conversationId);
        if (messages.isEmpty()) {
            return "No messages yet for conversation '" + conversationId + "'.";
        }
        return messages.stream()
                .map(message -> message.getMessageType() + ": " + message.getText())
                .collect(Collectors.joining("\n"));
    }

    @GetMapping("/clear")
    public String clear(@RequestParam(defaultValue = "demo-conversation") String conversationId) {
        chatMemory().clear(conversationId);
        return "Cleared conversation '" + conversationId + "'.";
    }

    // A thin, stateless window wrapper around the persistent repository - cheap to
    // build per call, since the actual conversation state lives in the database.
    private ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
    }
}
