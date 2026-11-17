package com.course.demo.memory;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
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

@RestController
@RequestMapping(path = "/api/chat-memory-demo", produces = MediaType.TEXT_PLAIN_VALUE)
@RequiredArgsConstructor
public class ChatMemoryDemo {

    private final ChatClient.Builder chatClientBuilder;

    // Held as a single instance (not rebuilt per request) so conversation history
    // actually persists across calls that share the same conversationId.
    private final ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(10)
            .build();

    // conversationId defaults to the caller's HTTP session id, so each user's browser
    // (via its session cookie) automatically stays pinned to its own conversation with
    // no client-side bookkeeping. An explicit conversationId is still accepted so this
    // endpoint remains testable with curl/Postman without needing a cookie jar.
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
                        .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                        .param(ChatMemory.CONVERSATION_ID, resolvedConversationId))
                .call()
                .content();

        return ChatMemoryResponse.builder()
                .conversationId(resolvedConversationId)
                .content(content)
                .build();
    }

    @GetMapping("/history")
    public String history(@RequestParam(defaultValue = "demo-conversation") String conversationId) {
        List<Message> messages = chatMemory.get(conversationId);
        if (messages.isEmpty()) {
            return "No messages yet for conversation '" + conversationId + "'.";
        }
        return messages.stream()
                .map(message -> message.getMessageType() + ": " + message.getText())
                .collect(Collectors.joining("\n"));
    }

    @GetMapping("/clear")
    public String clear(@RequestParam(defaultValue = "demo-conversation") String conversationId) {
        chatMemory.clear(conversationId);
        return "Cleared conversation '" + conversationId + "'.";
    }
}
