package com.rockyshen.easyaccountagent.tool;

import com.rockyshen.easyaccountagent.constant.ContentValues;
import com.rockyshen.easyaccountagent.dto.FlowAddRequestDto;
import com.rockyshen.easyaccountagent.dto.ScreenFlowRequestDto;
import com.rockyshen.easyaccountagent.service.LedgerFacade;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class EasyAccountsToolFunctions {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ConcurrentHashMap<String, Long> RECENT = new ConcurrentHashMap<>();

    private EasyAccountsToolFunctions() {
    }

    public record EmptyRequest(@ToolParam(description = "占位，传空字符串") String unused) {
    }

    public record ActionIdRequest(@ToolParam(description = "收支类型 actionId") int actionId) {
    }

    public record MonthlyFlowsRequest(
            @ToolParam(description = "3=全部,0=收入,1=支出,2=转账") int handle,
            @ToolParam(description = "0=日期降序,1=金额降序") int order,
            @ToolParam(description = "日期 yyyy-MM-dd") String date) {
    }

    public record SearchFlowsRequest(
            @ToolParam(description = "3=全部,0=收入,1=支出,2=转账") int chooseHandle,
            @ToolParam(description = "账户ID，0不限") int accountId,
            @ToolParam(description = "开始日期") String startDate,
            @ToolParam(description = "结束日期") String endDate,
            @ToolParam(description = "单月模式") boolean singleMonth,
            @ToolParam(description = "仅收藏") boolean collect,
            @ToolParam(description = "备注") String note) {
    }

    public record FlowIdRequest(@ToolParam(description = "流水 ID") int flowId) {
    }

    public record WriteFlowRequest(
            @ToolParam(description = "金额") String money,
            @ToolParam(description = "日期 yyyy-MM-dd") String date,
            @ToolParam(description = "账户 ID") int accountId,
            @ToolParam(description = "分类 typeId") int typeId,
            @ToolParam(description = "备注") String note) {
    }

    public record TransferRequest(
            @ToolParam(description = "金额") String money,
            @ToolParam(description = "日期") String date,
            @ToolParam(description = "源账户 ID") int accountId,
            @ToolParam(description = "目标账户 ID") int accountToId,
            @ToolParam(description = "分类 typeId") int typeId,
            @ToolParam(description = "备注") String note) {
    }

    public record UpdateFlowRequest(
            @ToolParam(description = "流水 ID") int flowId,
            @ToolParam(description = "金额") String money,
            @ToolParam(description = "日期") String date,
            @ToolParam(description = "actionId") int actionId,
            @ToolParam(description = "账户 ID") int accountId,
            @ToolParam(description = "目标账户，非转账填0") int accountToId,
            @ToolParam(description = "分类 typeId") int typeId,
            @ToolParam(description = "备注") String note) {
    }

    public record ToggleFavoriteRequest(
            @ToolParam(description = "流水 ID") int flowId,
            @ToolParam(description = "1收藏,0取消") int collect) {
    }

    public record CreateAccountRequest(
            @ToolParam(description = "账户名称") String name,
            @ToolParam(description = "普通账户=初始余额；信用卡=信用额度，必须大于0") String initialMoney,
            @ToolParam(description = "卡号，可选") String card,
            @ToolParam(description = "备注，可选") String note,
            @ToolParam(description = "账户类型：0=普通/储蓄，1=信用卡") int accountType) {
    }

    public record UpdateAccountRequest(
            @ToolParam(description = "账户 ID") int accountId,
            @ToolParam(description = "新名称，空字符串表示不修改") String name,
            @ToolParam(description = "新卡号，空字符串表示不修改") String card,
            @ToolParam(description = "新备注，空字符串表示不修改") String note,
            @ToolParam(description = "普通账户=豁免金额；信用卡=新信用额度（保持已用不变），空字符串表示不修改") String exemptMoney) {
    }

    public record AccountIdRequest(@ToolParam(description = "账户 ID") int accountId) {
    }

    public record RepayCreditRequest(
            @ToolParam(description = "还款金额") String money,
            @ToolParam(description = "日期 yyyy-MM-dd") String date,
            @ToolParam(description = "付款账户 ID（普通/储蓄账户）") int fromAccountId,
            @ToolParam(description = "信用卡账户 ID") int creditAccountId,
            @ToolParam(description = "分类 typeId") int typeId,
            @ToolParam(description = "备注") String note) {
    }

    public static Function<EmptyRequest, String> listAccounts(LedgerFacade facade) {
        return req -> facade.listAccounts();
    }

    public static Function<EmptyRequest, String> listActions(LedgerFacade facade) {
        return req -> facade.listActions();
    }

    public static Function<ActionIdRequest, String> listTypesByAction(LedgerFacade facade) {
        return req -> facade.listTypesByAction(req.actionId());
    }

    public static Function<MonthlyFlowsRequest, String> getMonthlyFlows(LedgerFacade facade) {
        return req -> facade.getMonthlyFlows(req.handle(), req.order(), req.date());
    }

    public static Function<SearchFlowsRequest, String> searchFlows(LedgerFacade facade) {
        return req -> {
            ScreenFlowRequestDto dto = new ScreenFlowRequestDto();
            dto.setChooseHandle(req.chooseHandle());
            dto.setAccountId(req.accountId());
            dto.setStartDate(req.startDate());
            dto.setEndDate(req.endDate());
            dto.setSingleMonth(req.singleMonth());
            dto.setCollect(req.collect());
            dto.setNote(req.note());
            return facade.searchFlows(dto);
        };
    }

    public static Function<FlowIdRequest, String> getFlowDetail(LedgerFacade facade) {
        return req -> facade.getFlowDetail(req.flowId());
    }

    public static Function<EmptyRequest, String> getDashboard(LedgerFacade facade) {
        return req -> facade.getDashboard();
    }

    public static Function<WriteFlowRequest, String> addExpense(LedgerFacade facade) {
        return req -> writeByHandle(facade, req, ContentValues.ACTION_SUB, 0);
    }

    public static Function<WriteFlowRequest, String> addIncome(LedgerFacade facade) {
        return req -> writeByHandle(facade, req, ContentValues.ACTION_ADD, 0);
    }

    public static Function<TransferRequest, String> transferMoney(LedgerFacade facade) {
        return req -> {
            int actionId = facade.findActionIdByHandle(ContentValues.ACTION_INNER);
            if (actionId < 0) {
                return "未找到转账类型 action。";
            }
            return facade.addFlow(buildRequest(req.money(), req.date(), req.accountId(), req.typeId(),
                    actionId, req.accountToId(), req.note(), false));
        };
    }

    public static Function<UpdateFlowRequest, String> updateFlow(LedgerFacade facade) {
        return req -> facade.updateFlow(req.flowId(), buildRequest(req.money(), req.date(), req.accountId(),
                req.typeId(), req.actionId(), req.accountToId(), req.note(), false));
    }

    public static Function<FlowIdRequest, String> deleteFlow(LedgerFacade facade) {
        return req -> facade.deleteFlow(req.flowId());
    }

    public static Function<ToggleFavoriteRequest, String> toggleFavorite(LedgerFacade facade) {
        return req -> facade.toggleFavorite(req.flowId(), req.collect());
    }

    public static Function<CreateAccountRequest, String> createAccount(LedgerFacade facade) {
        return req -> facade.createAccount(req.name(), req.initialMoney(), req.card(), req.note(), req.accountType());
    }

    public static Function<UpdateAccountRequest, String> updateAccount(LedgerFacade facade) {
        return req -> facade.updateAccount(req.accountId(), req.name(), req.card(), req.note(), req.exemptMoney());
    }

    public static Function<AccountIdRequest, String> deleteAccount(LedgerFacade facade) {
        return req -> facade.deleteAccount(req.accountId());
    }

    public static Function<RepayCreditRequest, String> repayCreditCard(LedgerFacade facade) {
        return req -> {
            String fp = "repay:" + req.money() + ":" + req.date() + ":" + req.fromAccountId() + ":" + req.creditAccountId();
            String dup = checkDuplicate(fp);
            if (dup != null) {
                return dup;
            }
            return facade.repayCreditCard(formatMoney(req.money()), resolveDate(req.date()),
                    req.fromAccountId(), req.creditAccountId(), req.typeId(), truncateNote(req.note()));
        };
    }

    private static String writeByHandle(LedgerFacade facade, WriteFlowRequest req, int handle, int accountToId) {
        int actionId = facade.findActionIdByHandle(handle);
        if (actionId < 0) {
            return "未找到 handle=" + handle + " 的 action。";
        }
        if (handle == ContentValues.ACTION_SUB) {
            String fp = "expense:" + req.money() + ":" + req.date() + ":" + req.accountId() + ":" + req.typeId();
            String dup = checkDuplicate(fp);
            if (dup != null) {
                return dup;
            }
        }
        return facade.addFlow(buildRequest(req.money(), req.date(), req.accountId(), req.typeId(),
                actionId, accountToId, req.note(), false));
    }

    private static FlowAddRequestDto buildRequest(String money, String date, int accountId, int typeId,
                                                   int actionId, int accountToId, String note, boolean collect) {
        FlowAddRequestDto dto = new FlowAddRequestDto();
        dto.setMoney(formatMoney(money));
        dto.setfDate(resolveDate(date));
        dto.setAccountId(accountId);
        dto.setTypeId(typeId);
        dto.setActionId(actionId);
        dto.setAccountToId(accountToId);
        dto.setNote(truncateNote(note));
        dto.setCollect(collect);
        return dto;
    }

    private static String formatMoney(String money) {
        return new BigDecimal(money).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String resolveDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now().format(DATE_FMT);
        }
        return date.trim();
    }

    private static String truncateNote(String note) {
        if (note == null) {
            return "";
        }
        return note.length() > 50 ? note.substring(0, 50) : note;
    }

    private static String checkDuplicate(String fingerprint) {
        long now = System.currentTimeMillis();
        RECENT.entrySet().removeIf(e -> now - e.getValue() > 30_000);
        Long prev = RECENT.putIfAbsent(fingerprint, now);
        if (prev != null && now - prev < 30_000) {
            return "疑似重复记账（30秒内相同请求），请确认后再试。";
        }
        return null;
    }
}
