package com.rockyshen.easyaccountagent.agent;

import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.rockyshen.easyaccountagent.constant.EasyAccountsPrompt;
import org.springframework.ai.chat.messages.SystemMessage;

/**
 * 每次模型调用时注入服务器当前日期，避免模型按训练截止年份误判「今天」。
 */
public class CurrentDateModelInterceptor extends ModelInterceptor {

    @Override
    public String getName() {
        return "CurrentDate";
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        SystemMessage systemMessage = request.getSystemMessage();
        String base = systemMessage == null ? null : systemMessage.getText();
        // 先剥离旧「当前日期」再注入，避免同一次对话里叠出两个今天
        SystemMessage enhanced = new SystemMessage(EasyAccountsPrompt.mergeSystemWithDateContext(base));
        return handler.call(ModelRequest.builder(request).systemMessage(enhanced).build());
    }
}
