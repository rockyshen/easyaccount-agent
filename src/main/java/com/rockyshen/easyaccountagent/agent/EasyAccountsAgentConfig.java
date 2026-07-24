package com.rockyshen.easyaccountagent.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.rockyshen.easyaccountagent.auth.AuthPropagatingToolCallback;
import com.rockyshen.easyaccountagent.constant.EasyAccountsPrompt;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

@Configuration
public class EasyAccountsAgentConfig {

    @Bean
    ReactAgent easyAccountAgent(
            @Qualifier("qwenClientModel") ChatModel chatModel,
            @Qualifier("easyAccountToolCallbacks") List<ToolCallback> easyAccountToolCallbacks,
            DataSource dataSource) {
        return ReactAgent.builder()
                .name("easyaccount_agent")
                .model(chatModel)
                .tools(AuthPropagatingToolCallback.wrapAll(
                        easyAccountToolCallbacks.toArray(new ToolCallback[0])))
                .systemPrompt(EasyAccountsPrompt.TEXT)
                .interceptors(new CurrentDateModelInterceptor())
                .saver(MysqlSaver.builder()
                        .dataSource(dataSource)
                        .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                        .build())
                .build();
    }

    @Bean
    List<ToolCallback> easyAccountToolCallbacks(
            @Qualifier("listAccountsTool") ToolCallback listAccountsTool,
            @Qualifier("listActionsTool") ToolCallback listActionsTool,
            @Qualifier("listTypesByActionTool") ToolCallback listTypesByActionTool,
            @Qualifier("getMonthlyFlowsTool") ToolCallback getMonthlyFlowsTool,
            @Qualifier("searchFlowsTool") ToolCallback searchFlowsTool,
            @Qualifier("getFlowDetailTool") ToolCallback getFlowDetailTool,
            @Qualifier("getDashboardTool") ToolCallback getDashboardTool,
            @Qualifier("addExpenseTool") ToolCallback addExpenseTool,
            @Qualifier("addIncomeTool") ToolCallback addIncomeTool,
            @Qualifier("transferMoneyTool") ToolCallback transferMoneyTool,
            @Qualifier("updateFlowTool") ToolCallback updateFlowTool,
            @Qualifier("deleteFlowTool") ToolCallback deleteFlowTool,
            @Qualifier("toggleFavoriteTool") ToolCallback toggleFavoriteTool,
            @Qualifier("createAccountTool") ToolCallback createAccountTool,
            @Qualifier("updateAccountTool") ToolCallback updateAccountTool,
            @Qualifier("deleteAccountTool") ToolCallback deleteAccountTool,
            @Qualifier("repayCreditCardTool") ToolCallback repayCreditCardTool) {
        return List.of(
                listAccountsTool, listActionsTool, listTypesByActionTool,
                getMonthlyFlowsTool, searchFlowsTool, getFlowDetailTool, getDashboardTool,
                addExpenseTool, addIncomeTool, transferMoneyTool,
                updateFlowTool, deleteFlowTool, toggleFavoriteTool,
                createAccountTool, updateAccountTool, deleteAccountTool, repayCreditCardTool);
    }
}
