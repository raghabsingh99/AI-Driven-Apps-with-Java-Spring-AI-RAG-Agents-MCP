package com.course.demo.advisor;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping(path = "/api/advisors-demo", produces = MediaType.TEXT_PLAIN_VALUE)
@RequiredArgsConstructor
public class AdvisorsDemo {

    private static final SimpleLoggerAdvisor CUSTOMIZED_SIMPLE_LOGGER = SimpleLoggerAdvisor.builder()
            .requestToString(request -> "user: " + request.prompt().getUserMessage().getText())
            .responseToString(response -> "assistant: " + response.getResult().getOutput().getText())
            .build();

    private static final SafeGuardAdvisor SAFE_GUARD_ADVISOR = SafeGuardAdvisor.builder()
            .sensitiveWords(List.of(
                    "password", "Password",
                    "credit card", "Credit Card", "Credit card",
                    "API key", "api key", "API Key"))
            .failureResponse("I can't help with requests involving passwords, credit card numbers, or API keys.")
            .build();

    private final ChatClient.Builder chatClientBuilder;

    @GetMapping("/logging")
    public Flux<String> logging(@RequestParam(defaultValue = "Spring AI") String topic) {
        return funFacts(topic, CUSTOMIZED_SIMPLE_LOGGER);
    }

    @GetMapping("/custom-logging")
    public Flux<String> customLogging(@RequestParam(defaultValue = "Spring AI") String topic) {
        return funFacts(topic, new CustomLoggerAdvisor());
    }

    @GetMapping("/safeguard")
    public String safeguard(@RequestParam(defaultValue = "What is my password?") String prompt) {
        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .advisors(new CustomLoggerAdvisor(), SAFE_GUARD_ADVISOR)
                .call()
                .content();
    }

    @GetMapping("/mutate-request")
    public String mutateRequest(@RequestParam(defaultValue = "What is Spring AI?") String prompt) {
        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .advisors(new PromptEnhancerAdvisor(), new CustomLoggerAdvisor())
                .call()
                .content();
    }

    @GetMapping("/disclaimer")
    public String disclaimer(@RequestParam(defaultValue = "What is Spring AI?") String prompt) {
        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .advisors(new DisclaimerAdvisor(), new CustomLoggerAdvisor())
                .call()
                .content();
    }

    @GetMapping("/timing")
    public String timing(@RequestParam(defaultValue = "What is Spring AI?") String prompt) {
        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .advisors(new TimingAdvisor(), new CustomLoggerAdvisor())
                .call()
                .content();
    }

    @GetMapping("/timing-stream")
    public Flux<String> timingStream(@RequestParam(defaultValue = "What is Spring AI?") String prompt) {
        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .advisors(new TimingAdvisor(), new CustomLoggerAdvisor())
                .stream()
                .content();
    }

    // Ordering (by Advisor#getOrder, ascending = outer -> inner):
    //   TimingAdvisor (-200) -> PromptEnhancerAdvisor (-100) -> CustomLoggerAdvisor (0) -> DisclaimerAdvisor (100) -> model
    // before() runs outer -> inner, after() runs inner -> outer, so the console log order is:
    //   [before] Timing starts -> [before] PromptEnhancer -> [before] CustomLogger (logs enhanced prompt)
    //   -> model call -> [after] Disclaimer (appends disclaimer first) -> [after] CustomLogger (logs disclaimed text)
    //   -> [after] Timing logs total elapsed ms (last, since it wraps everything else)
    @GetMapping("/chained")
    public String chained(@RequestParam(defaultValue = "What is Spring AI?") String prompt) {
        return chatClientBuilder.build()
                .prompt()
                .user(prompt)
                .advisors(new DisclaimerAdvisor(), new TimingAdvisor(), new CustomLoggerAdvisor(), new PromptEnhancerAdvisor())
                .call()
                .content();
    }

    private Flux<String> funFacts(String topic, Advisor advisor) {
        return chatClientBuilder.build()
                .prompt()
                .user("Write 5 fun facts about " + topic + ", one fact per line.")
                .advisors(advisor)
                .stream()
                .content();
    }
}
