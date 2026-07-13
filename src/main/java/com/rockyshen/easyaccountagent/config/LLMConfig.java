package com.rockyshen.easyaccountagent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author rockyshen
 * @date 2026/6/26 10:25
 */
@Configuration
public class LLMConfig {
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Bean(name = "qwenClientModel")
    public ChatModel qwen(){
        DashScopeChatModel chatModel = DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder().apiKey(apiKey).build())
                .defaultOptions(DashScopeChatOptions.builder().withModel("qwen-plus").build())
                .build();
        return chatModel;
    }

    @Bean(name = "qwenChatClient")
    public ChatClient qwenChatClient(@Qualifier("qwenClientModel") ChatModel qwen) {
        // new一个chatMemory，提供给MessageChatMemoryAdvisor
        // 打成 jar 部署：一般不应再往 jar 里的 classpath 写文件，那时应改成可配置目录（例如 application.yml 里配绝对路径，或 ${user.home}/.your-app/chat-memory）
        return ChatClient.builder(qwen)
                .build();
    }
}
