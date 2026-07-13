package com.rockyshen.easyaccountagent.controller;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatController {

    private final ReactAgent easyAccountAgent;

    public ChatController(@Qualifier("easyAccountAgent") ReactAgent easyAccountAgent) {
        this.easyAccountAgent = easyAccountAgent;
    }

    @GetMapping("/chat")
    public Flux<String> chat(@RequestParam(name = "msg") String msg) throws GraphRunnerException {
        return easyAccountAgent.streamMessages(msg)
                .filter(AssistantMessage.class::isInstance)
                .map(m -> ((AssistantMessage) m).getText());
    }
}
