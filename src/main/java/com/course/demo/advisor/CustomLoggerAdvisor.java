package com.course.demo.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;

public class CustomLoggerAdvisor implements BaseAdvisor {

    private static final Logger log = LoggerFactory.getLogger(CustomLoggerAdvisor.class);

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        log.info(">> user: {}", request.prompt().getUserMessage().getText());
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        log.info("<< assistant: {}", response.chatResponse().getResult().getOutput().getText());
        return response;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
