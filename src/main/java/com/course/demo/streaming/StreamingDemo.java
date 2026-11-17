package com.course.demo.streaming;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/streamed-demo")
@RequiredArgsConstructor
public class StreamingDemo {

    private final ChatClient.Builder chatClientBuilder;

    @GetMapping(value = "/fun-facts", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> funFacts(@RequestParam(defaultValue = "Spring AI") String topic) {
        return chatClientBuilder.build()
                .prompt()
                .user("Write 5 fun facts about " + topic + ", one fact per line.")
                .stream()
                .content();
    }
}
