package com.course.demo.advisor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

public class TimingAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TimingAdvisor.class);

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        long startTime = System.currentTimeMillis();
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        long elapsedMs = System.currentTimeMillis() - startTime;
        log.info("LLM call took {} ms", elapsedMs);
        return chatClientResponse;
    }

    @Override
    public String getName() {
        return TimingAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return -200;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        long startTime = System.currentTimeMillis();
        Flux<ChatClientResponse> responseFlux = streamAdvisorChain.nextStream(chatClientRequest);
        return responseFlux.doOnTerminate(() -> {
            long elapsedMs = System.currentTimeMillis() - startTime;
            log.info("LLM stream call took {} ms", elapsedMs);
        });
    }
}
