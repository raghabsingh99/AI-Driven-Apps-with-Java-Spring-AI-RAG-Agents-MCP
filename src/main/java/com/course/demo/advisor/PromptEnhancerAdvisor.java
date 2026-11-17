package com.course.demo.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.Prompt;

public class PromptEnhancerAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(PromptEnhancerAdvisor.class);
    private static final String INSTRUCTION = "Respond in no more than 2 sentences.";
    private static final String PERSONA = "You are a seasoned senior tech advisor: direct, pragmatic, and no-nonsense.";

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        log.info("[before] PromptEnhancerAdvisor: appending persona + instruction");
        Prompt mutatedPrompt = request.prompt()
                .augmentUserMessage(userMessage -> userMessage.mutate()
                        .text(userMessage.getText() + "\n\n" + PERSONA + "\n" + INSTRUCTION)
                        .build());

        return request.mutate()
                .prompt(mutatedPrompt)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
