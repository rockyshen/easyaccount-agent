package com.rockyshen.easyaccountagent.config;

import com.rockyshen.easyaccountagent.service.LedgerFacade;
import com.rockyshen.easyaccountagent.tool.EasyAccountsToolFunctions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EasyAccountsToolConfig {

    @Bean
    ToolCallback listAccountsTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("listAccounts", EasyAccountsToolFunctions.listAccounts(facade))
                .description("获取所有活跃账户及余额")
                .inputType(EasyAccountsToolFunctions.EmptyRequest.class)
                .build();
    }

    @Bean
    ToolCallback listActionsTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("listActions", EasyAccountsToolFunctions.listActions(facade))
                .description("获取收支类型列表")
                .inputType(EasyAccountsToolFunctions.EmptyRequest.class)
                .build();
    }

    @Bean
    ToolCallback listTypesByActionTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("listTypesByAction", EasyAccountsToolFunctions.listTypesByAction(facade))
                .description("获取指定收支类型的分类树")
                .inputType(EasyAccountsToolFunctions.ActionIdRequest.class)
                .build();
    }

    @Bean
    ToolCallback getMonthlyFlowsTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("getMonthlyFlows", EasyAccountsToolFunctions.getMonthlyFlows(facade))
                .description("查询月度流水列表")
                .inputType(EasyAccountsToolFunctions.MonthlyFlowsRequest.class)
                .build();
    }

    @Bean
    ToolCallback searchFlowsTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("searchFlows", EasyAccountsToolFunctions.searchFlows(facade))
                .description("高级筛选流水")
                .inputType(EasyAccountsToolFunctions.SearchFlowsRequest.class)
                .build();
    }

    @Bean
    ToolCallback getFlowDetailTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("getFlowDetail", EasyAccountsToolFunctions.getFlowDetail(facade))
                .description("获取单条流水详情")
                .inputType(EasyAccountsToolFunctions.FlowIdRequest.class)
                .build();
    }

    @Bean
    ToolCallback getDashboardTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("getDashboard", EasyAccountsToolFunctions.getDashboard(facade))
                .description("获取仪表盘：总资产、净资产、年度收支")
                .inputType(EasyAccountsToolFunctions.EmptyRequest.class)
                .build();
    }

    @Bean
    ToolCallback addExpenseTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("addExpense", EasyAccountsToolFunctions.addExpense(facade))
                .description("记录一笔支出")
                .inputType(EasyAccountsToolFunctions.WriteFlowRequest.class)
                .build();
    }

    @Bean
    ToolCallback addIncomeTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("addIncome", EasyAccountsToolFunctions.addIncome(facade))
                .description("记录一笔收入")
                .inputType(EasyAccountsToolFunctions.WriteFlowRequest.class)
                .build();
    }

    @Bean
    ToolCallback transferMoneyTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("transferMoney", EasyAccountsToolFunctions.transferMoney(facade))
                .description("内部转账")
                .inputType(EasyAccountsToolFunctions.TransferRequest.class)
                .build();
    }

    @Bean
    ToolCallback updateFlowTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("updateFlow", EasyAccountsToolFunctions.updateFlow(facade))
                .description("修改流水")
                .inputType(EasyAccountsToolFunctions.UpdateFlowRequest.class)
                .build();
    }

    @Bean
    ToolCallback deleteFlowTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("deleteFlow", EasyAccountsToolFunctions.deleteFlow(facade))
                .description("删除流水")
                .inputType(EasyAccountsToolFunctions.FlowIdRequest.class)
                .build();
    }

    @Bean
    ToolCallback toggleFavoriteTool(LedgerFacade facade) {
        return FunctionToolCallback.builder("toggleFavorite", EasyAccountsToolFunctions.toggleFavorite(facade))
                .description("收藏或取消收藏流水")
                .inputType(EasyAccountsToolFunctions.ToggleFavoriteRequest.class)
                .build();
    }
}
