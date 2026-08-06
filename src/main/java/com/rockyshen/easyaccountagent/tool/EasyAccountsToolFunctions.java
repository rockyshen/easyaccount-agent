package com.rockyshen.easyaccountagent.tool;

import com.rockyshen.easyaccountagent.constant.ContentValues;
import com.rockyshen.easyaccountagent.dto.FlowAddRequestDto;
import com.rockyshen.easyaccountagent.dto.ScreenFlowRequestDto;
import com.rockyshen.easyaccountagent.service.LedgerFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

public final class EasyAccountsToolFunctions {

    private static final Logger log = LoggerFactory.getLogger(EasyAccountsToolFunctions.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ConcurrentHashMap<String, Long> RECENT = new ConcurrentHashMap<>();
    private static final String EXPLICIT_DATE_DESC =
            "仅当用户明确说出具体日期或相对日期（今天除外）时填 yyyy-MM-dd；"
                    + "用户说「今天」或完全没提日期时，必须传空字符串，由服务端填当前日期。"
                    + "禁止自行推断今天是哪一天。";

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
            @ToolParam(description = EXPLICIT_DATE_DESC) String explicitDate,
            @ToolParam(description = "账户 ID") int accountId,
            @ToolParam(description = "分类 typeId") int typeId,
            @ToolParam(description = "备注") String note) {
    }

    public record TransferRequest(
            @ToolParam(description = "金额") String money,
            @ToolParam(description = EXPLICIT_DATE_DESC) String explicitDate,
            @ToolParam(description = "源账户 ID") int accountId,
            @ToolParam(description = "目标账户 ID") int accountToId,
            @ToolParam(description = "分类 typeId") int typeId,
            @ToolParam(description = "备注") String note) {
    }

    public record UpdateFlowRequest(
            @ToolParam(description = "流水 ID") int flowId,
            @ToolParam(description = "金额") String money,
            @ToolParam(description = "修改流水时：用户未改日期则填原流水日期；用户明确改日期时填新 yyyy-MM-dd；"
                    + "禁止自行推断今天。仅当用户说「改成今天」时传空字符串。") String explicitDate,
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

    public record CreateTypeRequest(
            @ToolParam(description = "分类名称") String name,
            @ToolParam(description = "收支类型 actionId，须先 listActions 获取") int actionId,
            @ToolParam(description = "父分类 ID；0 表示一级分类，大于 0 表示挂到该一级分类下") int parent) {
    }

    public record UpdateTypeRequest(
            @ToolParam(description = "分类 ID，操作前应先 listTypesByAction 确认") int typeId,
            @ToolParam(description = "新名称") String name,
            @ToolParam(description = "新 actionId；0 表示不修改") int actionId,
            @ToolParam(description = "新父分类 ID；-1 表示不修改；0 表示改为一级分类；大于 0 表示挂到该一级分类下") int parent) {
    }

    public record TypeIdRequest(@ToolParam(description = "分类 ID") int typeId) {
    }

    public record RepayCreditRequest(
            @ToolParam(description = "还款金额") String money,
            @ToolParam(description = EXPLICIT_DATE_DESC) String explicitDate,
            @ToolParam(description = "付款账户 ID（普通/储蓄账户）") int fromAccountId,
            @ToolParam(description = "信用卡账户 ID") int creditAccountId,
            @ToolParam(description = "分类 typeId") int typeId,
            @ToolParam(description = "备注") String note) {
    }

    public static Function<EmptyRequest, String> listAccounts(LedgerFacade facade) {
        return req -> facade.listAccounts();
    }

    public static Function<EmptyRequest, String> getOnboardingStatus(LedgerFacade facade) {
        return req -> facade.getOnboardingStatus();
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
        return req -> withDateValidation(() -> {
            int actionId = facade.findActionIdByHandle(ContentValues.ACTION_INNER);
            if (actionId < 0) {
                return "未找到转账类型 action。";
            }
            return facade.addFlow(buildRequest(req.money(), req.explicitDate(), req.accountId(), req.typeId(),
                    actionId, req.accountToId(), req.note(), false));
        });
    }

    public static Function<UpdateFlowRequest, String> updateFlow(LedgerFacade facade) {
        return req -> withDateValidation(() -> facade.updateFlow(req.flowId(),
                buildRequest(req.money(), req.explicitDate(), req.accountId(),
                        req.typeId(), req.actionId(), req.accountToId(), req.note(), false)));
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

    public static Function<CreateTypeRequest, String> createType(LedgerFacade facade) {
        return req -> facade.createType(req.name(), req.actionId(), req.parent());
    }

    public static Function<UpdateTypeRequest, String> updateType(LedgerFacade facade) {
        return req -> facade.updateType(req.typeId(), req.name(), req.actionId(), req.parent());
    }

    public static Function<TypeIdRequest, String> deleteType(LedgerFacade facade) {
        return req -> facade.deleteType(req.typeId());
    }

    public static Function<RepayCreditRequest, String> repayCreditCard(LedgerFacade facade) {
        return req -> withDateValidation(() -> {
            String fp = "repay:" + req.money() + ":" + req.explicitDate() + ":"
                    + req.fromAccountId() + ":" + req.creditAccountId();
            String dup = checkDuplicate(fp);
            if (dup != null) {
                return dup;
            }
            return facade.repayCreditCard(formatMoney(req.money()), resolveDate(req.explicitDate()),
                    req.fromAccountId(), req.creditAccountId(), req.typeId(), truncateNote(req.note()));
        });
    }

    private static String writeByHandle(LedgerFacade facade, WriteFlowRequest req, int handle, int accountToId) {
        return withDateValidation(() -> {
            int actionId = facade.findActionIdByHandle(handle);
            if (actionId < 0) {
                return "未找到 handle=" + handle + " 的 action。";
            }
            if (handle == ContentValues.ACTION_SUB) {
                String fp = "expense:" + req.money() + ":" + req.explicitDate() + ":"
                        + req.accountId() + ":" + req.typeId();
                String dup = checkDuplicate(fp);
                if (dup != null) {
                    return dup;
                }
            }
            return facade.addFlow(buildRequest(req.money(), req.explicitDate(), req.accountId(), req.typeId(),
                    actionId, accountToId, req.note(), false));
        });
    }

    private static FlowAddRequestDto buildRequest(String money, String explicitDate, int accountId, int typeId,
                                                   int actionId, int accountToId, String note, boolean collect) {
        FlowAddRequestDto dto = new FlowAddRequestDto();
        dto.setMoney(formatMoney(money));
        dto.setfDate(resolveDate(explicitDate));
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

    /**
     * 空/空白 → 服务端当天（Asia/Shanghai）；非空 → 校验格式与不得晚于今天。
     */
    private static String resolveDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(APP_ZONE).format(DATE_FMT);
        }
        String trimmed = date.trim();
        String today = LocalDate.now(APP_ZONE).format(DATE_FMT);
        if (!trimmed.equals(today)) {
            log.warn("model_explicit_date_not_today explicitDate={} serverToday={}", trimmed, today);
        }
        return validateDate(trimmed);
    }

    private static String validateDate(String date) {
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(date, DATE_FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式必须为 yyyy-MM-dd，收到：" + date);
        }
        LocalDate today = LocalDate.now(APP_ZONE);
        if (parsed.isAfter(today)) {
            throw new IllegalArgumentException("不能记录未来日期：" + date + "，当前日期 " + today);
        }
        return date;
    }

    private static String withDateValidation(Supplier<String> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
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
