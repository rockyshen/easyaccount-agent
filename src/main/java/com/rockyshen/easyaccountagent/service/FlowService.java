package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.auth.AuthContext;
import com.rockyshen.easyaccountagent.dto.FlowListDto;
import com.rockyshen.easyaccountagent.dto.FlowAddRequestDto;
import com.rockyshen.easyaccountagent.dto.FlowSingleResponseDto;
import com.rockyshen.easyaccountagent.dto.TypeListResponseDto;
import com.rockyshen.easyaccountagent.entity.Account;
import com.rockyshen.easyaccountagent.entity.Action;
import com.rockyshen.easyaccountagent.entity.Flow;
import com.rockyshen.easyaccountagent.dao.FlowDao;
import com.rockyshen.easyaccountagent.entity.Type;
import com.rockyshen.easyaccountagent.constant.ContentValues;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class FlowService {

    @Autowired
    ActionService actionService;

    @Autowired
    AccountService accountService;

    @Autowired
    FlowDao flowDao;

    @Autowired
    TypeService typeService;


    @Transactional(rollbackFor = Exception.class)
    public void doAddFlow(FlowAddRequestDto flowAddRequestDto) throws Exception {
        int userId = AuthContext.requireUserId();
        Flow flow = setNewFlow(flowAddRequestDto);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        String createDate = sdf.format(new Date());
        flow.setFCreateDate(createDate);
        BeanUtils.copyProperties(flowAddRequestDto, flow);
        flow.setUserId(userId);
        flowDao.addFlow(flow);
    }

    private Flow setNewFlow(FlowAddRequestDto flowAddRequestDto) throws Exception {
        Action action = actionService.getAction(flowAddRequestDto.getActionId());
        Account account = accountService.getOriginAccountById(flowAddRequestDto.getAccountId());
        if (account == null) {
            throw new Exception("账户不存在或不属于当前用户");
        }
        Account toAccount = null;
        BigDecimal flowMoney = new BigDecimal(flowAddRequestDto.getMoney());
        BigDecimal accountMoney = new BigDecimal(nullToZero(account.getMoney()));
        switch (action.getHandle()) {
            case ContentValues.ACTION_ADD:
                account = handleAccount(ContentValues.ACTION_ADD, flowAddRequestDto.getMoney(), account, action.isExempt());
                break;
            case ContentValues.ACTION_SUB:
                if (accountMoney.compareTo(flowMoney) < 0) {
                    throw new Exception(insufficientBalanceMessage(account));
                }
                account = handleAccount(ContentValues.ACTION_SUB, flowAddRequestDto.getMoney(), account, action.isExempt());
                break;
            case ContentValues.ACTION_INNER:
                if (accountMoney.compareTo(flowMoney) < 0) {
                    throw new Exception(insufficientBalanceMessage(account));
                }
                toAccount = accountService.getOriginAccountById(flowAddRequestDto.getAccountToId());
                if (toAccount == null) {
                    throw new Exception("目标账户不存在或不属于当前用户");
                }
                toAccount = handleAccount(ContentValues.ACTION_ADD, flowAddRequestDto.getMoney(), toAccount, action.isExempt());
                accountService.updateOriginAccount(toAccount);
                account = handleAccount(ContentValues.ACTION_SUB, flowAddRequestDto.getMoney(), account, action.isExempt());
                break;
        }
        accountService.updateOriginAccount(account);

        Flow flow = new Flow();
        boolean creditRelated = AccountService.isCreditAccount(account) || AccountService.isCreditAccount(toAccount);
        flow.setExempt(action.isExempt() || creditRelated);
        flow.setUserId(AuthContext.requireUserId());
        BeanUtils.copyProperties(flowAddRequestDto, flow);
        return flow;
    }

    public void doCollectFlow(int id, int collect) {
        flowDao.collectFlowById(id, AuthContext.requireUserId(), collect);
    }

    @Transactional(rollbackFor = Exception.class)
    public void doUpdateFlow(int id, FlowAddRequestDto flowAddRequestDto) throws Exception {
        int userId = AuthContext.requireUserId();
        List<Flow> found = flowDao.queryFlowById(id, userId);
        if (found == null || found.isEmpty()) {
            throw new Exception("流水不存在或不属于当前用户");
        }
        Flow lastFlow = found.get(0);
        Action lastAction = actionService.getAction(lastFlow.getActionId());
        Account lastAccount = accountService.getOriginAccountById(lastFlow.getAccountId());
        switch (lastAction.getHandle()) {
            case ContentValues.ACTION_ADD:
                lastAccount = handleAccount(ContentValues.ACTION_SUB, lastFlow.getMoney(), lastAccount, lastAction.isExempt());
                break;
            case ContentValues.ACTION_SUB:
                lastAccount = handleAccount(ContentValues.ACTION_ADD, lastFlow.getMoney(), lastAccount, lastAction.isExempt());
                break;
            case ContentValues.ACTION_INNER:
                Account lastToAccount = accountService.getOriginAccountById(lastFlow.getAccountToId());
                lastToAccount = handleAccount(ContentValues.ACTION_SUB, lastFlow.getMoney(), lastToAccount, lastAction.isExempt());
                accountService.updateOriginAccount(lastToAccount);
                lastAccount = handleAccount(ContentValues.ACTION_ADD, lastFlow.getMoney(), lastAccount, lastAction.isExempt());
                break;
        }
        accountService.updateOriginAccount(lastAccount);
        Flow flow = setNewFlow(flowAddRequestDto);
        flow.setId(id);
        BeanUtils.copyProperties(flowAddRequestDto, flow);
        flow.setUserId(userId);
        flowDao.updateFlow(flow);
    }

    /**
     * 信用卡：money=可用额度，exemptMoney=信用额度（记账时不改额度）。
     * 普通账户：仍按 action.exempt 同步豁免金额（兼容旧逻辑）。
     */
    private Account handleAccount(int handle, String money, Account account, boolean actionExempt) throws Exception {
        if (AccountService.isCreditAccount(account)) {
            return handleCreditAccount(handle, money, account);
        }
        BigDecimal flowMoney = new BigDecimal(money);
        BigDecimal accountMoney = new BigDecimal(nullToZero(account.getMoney()));
        BigDecimal accountExemptMoney = actionExempt ? new BigDecimal(nullToZero(account.getExemptMoney())) : null;
        switch (handle) {
            case ContentValues.ACTION_ADD:
                accountMoney = accountMoney.add(flowMoney);
                if (actionExempt) {
                    accountExemptMoney = accountExemptMoney.add(flowMoney);
                    account.setExemptMoney(accountExemptMoney.toPlainString());
                }
                break;
            case ContentValues.ACTION_SUB:
                accountMoney = accountMoney.subtract(flowMoney);
                if (actionExempt) {
                    accountExemptMoney = accountExemptMoney.subtract(flowMoney);
                    account.setExemptMoney(accountExemptMoney.toPlainString());
                }
                break;
        }
        account.setMoney(accountMoney.toPlainString());
        return account;
    }

    private Account handleCreditAccount(int handle, String money, Account account) throws Exception {
        BigDecimal flowMoney = new BigDecimal(money);
        BigDecimal available = new BigDecimal(nullToZero(account.getMoney()));
        BigDecimal limit = new BigDecimal(nullToZero(account.getExemptMoney()));
        switch (handle) {
            case ContentValues.ACTION_ADD:
                // 还款/贷方增加：提升可用额度，不超过信用额度
                available = available.add(flowMoney);
                if (available.compareTo(limit) > 0) {
                    throw new Exception("还款金额超过已用额度，可用额度不能大于信用额度 "
                            + limit.setScale(2, RoundingMode.HALF_UP).toPlainString());
                }
                break;
            case ContentValues.ACTION_SUB:
                // 刷卡支出/取现：扣减可用额度
                if (available.compareTo(flowMoney) < 0) {
                    throw new Exception("信用卡可用额度不足");
                }
                available = available.subtract(flowMoney);
                break;
            default:
                break;
        }
        account.setMoney(available.setScale(2, RoundingMode.HALF_UP).toPlainString());
        return account;
    }

    private static String insufficientBalanceMessage(Account account) {
        return AccountService.isCreditAccount(account) ? "信用卡可用额度不足" : "减少金额不允许大于账户金额";
    }

    private static String nullToZero(String value) {
        return value == null || value.isBlank() ? "0" : value;
    }

    @Transactional(rollbackFor = Exception.class)
    public FlowSingleResponseDto doQueryFlow(int id) {
        List<Flow> flows = flowDao.queryFlowById(id, AuthContext.requireUserId());
        if (flows == null || flows.size() == 0) {
            return null;
        }
        FlowSingleResponseDto toClientBean = new FlowSingleResponseDto();
        Flow flow = flows.get(0);
        BeanUtils.copyProperties(flow, toClientBean);
        Account account = accountService.getOriginAccountById(flow.getAccountId());
        Action action = actionService.getAction(flow.getActionId());
        Type type = typeService.queryTypeSingle(flow.getTypeId());
        TypeListResponseDto typeListResponseDto = new TypeListResponseDto();
        typeListResponseDto.convertToDto(type);
        if (type.getParent() != -1) {
            Type parentType = typeService.queryTypeSingle(type.getParent());
            typeListResponseDto.setTName(parentType.getTName() + "——" + type.getTName());
        }
        if (action.getHandle() == ContentValues.ACTION_INNER) {
            Account toAccount = accountService.getOriginAccountById(flow.getAccountToId());
            toClientBean.setAccountTo(toAccount);
        }
        toClientBean.setType(typeListResponseDto);
        toClientBean.setAccount(account);
        toClientBean.setAction(action);
        return toClientBean;
    }

    @Transactional(rollbackFor = Exception.class)
    public void doDeleteFlow(int id) throws Exception {
        int userId = AuthContext.requireUserId();
        List<Flow> found = flowDao.queryFlowById(id, userId);
        if (found == null || found.isEmpty()) {
            throw new Exception("流水不存在或不属于当前用户");
        }
        Flow flow = found.get(0);
        Action lastAction = actionService.getAction(flow.getActionId());
        Account lastAccount = accountService.getOriginAccountById(flow.getAccountId());
        switch (lastAction.getHandle()) {
            case ContentValues.ACTION_ADD:
                lastAccount = handleAccount(ContentValues.ACTION_SUB, flow.getMoney(), lastAccount, lastAction.isExempt());
                break;
            case ContentValues.ACTION_SUB:
                lastAccount = handleAccount(ContentValues.ACTION_ADD, flow.getMoney(), lastAccount, lastAction.isExempt());
                break;
            case ContentValues.ACTION_INNER:
                Account lastToAccount = accountService.getOriginAccountById(flow.getAccountToId());
                lastToAccount = handleAccount(ContentValues.ACTION_SUB, flow.getMoney(), lastToAccount, lastAction.isExempt());
                accountService.updateOriginAccount(lastToAccount);
                lastAccount = handleAccount(ContentValues.ACTION_ADD, flow.getMoney(), lastAccount, lastAction.isExempt());
                break;
        }

        accountService.updateOriginAccount(lastAccount);
        flowDao.deleteFlowById(id, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public FlowListDto doGetMainBean(int handle, int order, String date) {
        String monthStr = date.substring(0, 7) + "%";
        FlowListDto flowListDto = new FlowListDto();
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyyMMdd_HHmm");
        String time = sdf1.format(new Date());
        log.info("time:  " + time + "   date: " + monthStr + "  handle: " + handle);
        List<Map<String, Object>> maps = flowDao.getFlowByMain(handle, order, monthStr, AuthContext.requireUserId());
        List<FlowListDto.FlowListSingleDto> flows = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        BigDecimal moneyIn = new BigDecimal("0");
        BigDecimal moneyOut = new BigDecimal("0");
        for (Map<String, Object> map : maps) {
            FlowListDto.FlowListSingleDto flow = new FlowListDto.FlowListSingleDto();
            flow.setId((Integer) map.get("id"));
            flow.setMoney((String) map.get("money"));
            flow.setExempt((Boolean) map.get("exempt"));
            flow.setCollect((Boolean) map.get("collect"));
            flow.setHandle((Integer) map.get("handle"));
            flow.setHName((String) map.get("h_name"));
            Date fDate = (Date) map.get("f_date");

            flow.setFDate(sdf.format(fDate));
            flow.setAName((String) map.get("a_name"));
            flow.setNote((String) map.get("note"));
            flow.setToAName((String) map.get("t_a_name"));

            if (map.get("p_t_name") != null) {
                flow.setTName(map.get("p_t_name") + "/" + map.get("t_name"));
            } else {
                flow.setTName((String) map.get("t_name"));
            }
            flows.add(flow);
            if (flow.getHandle() == 1) {
                moneyOut = moneyOut.add(new BigDecimal(flow.getMoney()));
            } else if (flow.getHandle() == 0) {
                moneyIn = moneyIn.add(new BigDecimal(flow.getMoney()));
            }
        }

        flowListDto.setTotalIn(moneyIn.toString());
        flowListDto.setTotalOut(moneyOut.toString());
        flowListDto.setFlows(flows);
        return flowListDto;
    }

}
