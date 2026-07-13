package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.dto.FlowAddRequestDto;
import com.rockyshen.easyaccountagent.dto.FlowListDto;
import com.rockyshen.easyaccountagent.dto.FlowSingleResponseDto;
import com.rockyshen.easyaccountagent.dto.HomeDto;
import com.rockyshen.easyaccountagent.dto.ScreenFlowRequestDto;
import com.rockyshen.easyaccountagent.dto.TypeListResponseDto;
import com.rockyshen.easyaccountagent.entity.Action;
import com.rockyshen.easyaccountagent.entity.Type;
import com.rockyshen.easyaccountagent.service.AccountService;
import com.rockyshen.easyaccountagent.service.ActionService;
import com.rockyshen.easyaccountagent.service.FlowService;
import com.rockyshen.easyaccountagent.service.HomeService;
import com.rockyshen.easyaccountagent.service.ScreenService;
import com.rockyshen.easyaccountagent.service.TypeService;
import com.rockyshen.easyaccountagent.constant.ContentValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class LedgerFacade {

    private final AccountService accountService;
    private final ActionService actionService;
    private final TypeService typeService;
    private final FlowService flowService;
    private final HomeService homeService;
    private final ScreenService screenService;

    public String listAccounts() {
        var accounts = accountService.getAllAccount();
        if (accounts.isEmpty()) {
            return "暂无活跃账户。";
        }
        StringBuilder sb = new StringBuilder("账户列表：\n");
        accounts.forEach(a -> sb.append(String.format("- id=%d, 名称=%s, 余额=%s, 豁免=%s%n",
                a.getId(), a.getName(), a.getMoney(), a.getExemptMoney())));
        return sb.toString();
    }

    public String listActions() {
        List<Action> actions = actionService.getActions();
        if (actions.isEmpty()) {
            return "暂无收支类型。";
        }
        StringBuilder sb = new StringBuilder("收支类型：\n");
        for (Action action : actions) {
            sb.append(String.format("- id=%d, 名称=%s, handle=%d (%s)%n",
                    action.getId(), action.getHName(), action.getHandle(), handleLabel(action.getHandle())));
        }
        return sb.toString();
    }

    public String listTypesByAction(int actionId) {
        List<TypeListResponseDto> types = typeService.queryTypeByActionId(actionId);
        if (types.isEmpty()) {
            return "该收支类型下暂无分类。";
        }
        StringBuilder sb = new StringBuilder("分类树（actionId=" + actionId + "）：\n");
        for (TypeListResponseDto parent : types) {
            sb.append(String.format("  父类 id=%d, 名称=%s%n", parent.getId(), parent.getTName()));
            if (parent.getChildrenTypes() != null) {
                for (TypeListResponseDto child : parent.getChildrenTypes()) {
                    sb.append(String.format("    子类 id=%d, 名称=%s%n", child.getId(), child.getTName()));
                }
            }
        }
        return sb.toString();
    }

    public String getMonthlyFlows(int handle, int order, String date) {
        try {
            FlowListDto dto = flowService.doGetMainBean(handle, order, date);
            StringBuilder sb = new StringBuilder(String.format("月度流水（%s）：收入=%s, 支出=%s%n",
                    date.substring(0, 7), dto.getTotalIn(), dto.getTotalOut()));
            if (dto.getFlows() == null || dto.getFlows().isEmpty()) {
                sb.append("无流水记录。");
                return sb.toString();
            }
            dto.getFlows().forEach(f -> sb.append(String.format("- id=%d, %s, %s, %s, %s, 备注=%s%n",
                    f.getId(), f.getFDate(), f.getHName(), f.getMoney(), f.getAName(),
                    f.getNote() == null ? "" : f.getNote())));
            return sb.toString();
        } catch (Exception e) {
            return "查询月度流水失败：" + e.getMessage();
        }
    }

    public String searchFlows(ScreenFlowRequestDto request) {
        try {
            FlowListDto dto = screenService.getFlowByScreen(request);
            StringBuilder sb = new StringBuilder(String.format("筛选结果：收入=%s, 支出=%s, 共%d条%n",
                    dto.getTotalIn(), dto.getTotalOut(),
                    dto.getFlows() == null ? 0 : dto.getFlows().size()));
            if (dto.getFlows() != null) {
                dto.getFlows().forEach(f -> sb.append(String.format("- id=%d, %s, %s %s, %s%n",
                        f.getId(), f.getFDate(), f.getHName(), f.getMoney(), f.getTName())));
            }
            return sb.toString();
        } catch (Exception e) {
            return "筛选流水失败：" + e.getMessage();
        }
    }

    public String getFlowDetail(int flowId) {
        try {
            FlowSingleResponseDto dto = flowService.doQueryFlow(flowId);
            if (dto == null) {
                return "未找到 id=" + flowId + " 的流水。";
            }
            return String.format("流水详情：id=%d, 日期=%s, 金额=%s, 账户=%s, 分类=%s, 备注=%s",
                    dto.getId(), dto.getFDate(), dto.getMoney(),
                    dto.getAccount() != null ? dto.getAccount().getAName() : "",
                    dto.getType() != null ? dto.getType().getTName() : "",
                    dto.getNote() == null ? "" : dto.getNote());
        } catch (Exception e) {
            return "查询流水详情失败：" + e.getMessage();
        }
    }

    public String getDashboard() {
        try {
            HomeDto home = homeService.getHomeBean();
            StringJoiner accounts = new StringJoiner(", ");
            if (home.getAccounts() != null) {
                home.getAccounts().forEach(a -> accounts.add(a.getAccountName() + ":" + a.getAccountAsset()));
            }
            return String.format("仪表盘：总资产=%s, 净资产=%s, 本年收入=%s, 本年支出=%s, 本年结余=%s%n账户：%s",
                    home.getTotalAsset(), home.getNetAsset(),
                    home.getYearIncome(), home.getYearOutCome(), home.getYearBalance(),
                    accounts);
        } catch (Exception e) {
            return "查询仪表盘失败：" + e.getMessage();
        }
    }

    public String addFlow(FlowAddRequestDto dto) {
        try {
            flowService.doAddFlow(dto);
            return String.format("记账成功：%s %s元，日期=%s", resolveTypeName(dto.getTypeId()), dto.getMoney(), dto.getfDate());
        } catch (Exception e) {
            return "记账失败：" + e.getMessage();
        }
    }

    public String updateFlow(int flowId, FlowAddRequestDto dto) {
        try {
            flowService.doUpdateFlow(flowId, dto);
            return "流水 id=" + flowId + " 已更新。";
        } catch (Exception e) {
            return "更新流水失败：" + e.getMessage();
        }
    }

    public String deleteFlow(int flowId) {
        try {
            flowService.doDeleteFlow(flowId);
            return "流水 id=" + flowId + " 已删除。";
        } catch (Exception e) {
            return "删除流水失败：" + e.getMessage();
        }
    }

    public String toggleFavorite(int flowId, int collect) {
        try {
            flowService.doCollectFlow(flowId, collect);
            return collect == 1 ? "已收藏流水 id=" + flowId : "已取消收藏流水 id=" + flowId;
        } catch (Exception e) {
            return "收藏操作失败：" + e.getMessage();
        }
    }

    public int findActionIdByHandle(int handle) {
        return actionService.getActions().stream()
                .filter(a -> a.getHandle() == handle)
                .map(Action::getId)
                .findFirst()
                .orElse(-1);
    }

    private String resolveTypeName(int typeId) {
        Type type = typeService.queryTypeSingle(typeId);
        if (type == null) {
            return "分类#" + typeId;
        }
        if (type.getParent() != null && type.getParent() != -1) {
            Type parent = typeService.queryTypeSingle(type.getParent());
            if (parent != null) {
                return parent.getTName() + "/" + type.getTName();
            }
        }
        return type.getTName();
    }

    private static String handleLabel(int handle) {
        return switch (handle) {
            case ContentValues.ACTION_ADD -> "收入";
            case ContentValues.ACTION_SUB -> "支出";
            case ContentValues.ACTION_INNER -> "转账";
            default -> "未知";
        };
    }
}
