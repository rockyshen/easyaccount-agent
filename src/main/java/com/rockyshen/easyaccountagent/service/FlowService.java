package com.rockyshen.easyaccountagent.service;

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
        Flow flow = setNewFlow(flowAddRequestDto);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        String createDate = sdf.format(new Date());
        flow.setFCreateDate(createDate);
        BeanUtils.copyProperties(flowAddRequestDto, flow);
        flowDao.addFlow(flow);
    }

    private Flow setNewFlow(FlowAddRequestDto flowAddRequestDto) throws Exception {
        Action action = actionService.getAction(flowAddRequestDto.getActionId());
        Account account = accountService.getOriginAccountById(flowAddRequestDto.getAccountId() );
        Account toAccount = null;
        BigDecimal flowMoney = new BigDecimal(flowAddRequestDto.getMoney());
        BigDecimal accountMoney = new BigDecimal(account.getMoney());
        switch (action.getHandle()) {
            case ContentValues.ACTION_ADD:
                account = handleAccount(ContentValues.ACTION_ADD, flowAddRequestDto.getMoney(), account, action.isExempt());
                break;
            case ContentValues.ACTION_SUB:
                if (accountMoney.compareTo(flowMoney) < 0) {
                    throw new Exception("减少金额不允许大于账户金额");
                }
                account = handleAccount(ContentValues.ACTION_SUB, flowAddRequestDto.getMoney(), account, action.isExempt());
                break;
            case ContentValues.ACTION_INNER:
                if (accountMoney.compareTo(flowMoney) < 0) {
                    throw new Exception("减少金额不允许大于账户金额");
                }
                toAccount = accountService.getOriginAccountById(flowAddRequestDto.getAccountToId());
                toAccount = handleAccount(ContentValues.ACTION_ADD, flowAddRequestDto.getMoney(), toAccount, action.isExempt());
                accountService.updateOriginAccount(toAccount);
                account = handleAccount(ContentValues.ACTION_SUB, flowAddRequestDto.getMoney(), account, action.isExempt());
                break;
        }
        accountService.updateOriginAccount(account);

        Flow flow = new Flow();
        flow.setExempt(action.isExempt());
        BeanUtils.copyProperties(flowAddRequestDto, flow);
        return flow;
    }

    public void doCollectFlow(int id, int collect) {
        flowDao.collectFlowById(id, collect);
    }

    @Transactional(rollbackFor = Exception.class)
    public void doUpdateFlow(int id, FlowAddRequestDto flowAddRequestDto) throws Exception {
        Flow lastFlow = flowDao.queryFlowById(id).get(0);
        Action lastAction = actionService.getAction(lastFlow.getActionId());
        Account lastAccount = accountService.getOriginAccountById(lastFlow.getAccountId() );
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
        flowDao.updateFlow(flow);
    }

    private Account handleAccount(int handle, String money, Account account, boolean isExempt) {
        BigDecimal flowMoney = new BigDecimal(money);
        BigDecimal accountMoney = new BigDecimal(account.getMoney());
        BigDecimal accountExemptMoney = isExempt ? new BigDecimal(account.getExemptMoney()) : null;
        switch (handle) {
            case ContentValues.ACTION_ADD:
                accountMoney = accountMoney.add(flowMoney);
                if (isExempt) {
                    accountExemptMoney = accountExemptMoney.add(flowMoney);
                    account.setExemptMoney(accountExemptMoney.toString());
                }
                break;
            case ContentValues.ACTION_SUB:
                accountMoney = accountMoney.subtract(flowMoney);
                if (isExempt) {
                    accountExemptMoney = accountExemptMoney.subtract(flowMoney);
                    account.setExemptMoney(accountExemptMoney.toString());
                }
                break;
        }
        account.setMoney(accountMoney.toString());
        return account;
    }

    @Transactional(rollbackFor = Exception.class)
    public FlowSingleResponseDto doQueryFlow(int id) {
        List<Flow> flows = flowDao.queryFlowById(id);
        if (flows == null || flows.size() == 0) {
            return null;
        }
        FlowSingleResponseDto toClientBean = new FlowSingleResponseDto();
        Flow flow = flows.get(0);
        BeanUtils.copyProperties(flow, toClientBean);
        Account account = accountService.getOriginAccountById(flow.getAccountId() );
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
        Flow flow = flowDao.queryFlowById(id).get(0);
        Action lastAction = actionService.getAction(flow.getActionId());
        Account lastAccount = accountService.getOriginAccountById(flow.getAccountId() );
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
        flowDao.deleteFlowById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public FlowListDto doGetMainBean(int handle, int order, String date) {
        String monthStr = date.substring(0, 7) + "%";
        FlowListDto flowListDto = new FlowListDto();
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyyMMdd_HHmm");
        String time = sdf1.format(new Date());
        log.info("time:  "+time+"   date: " + monthStr + "  handle: " + handle);
        List<Map<String, Object>> maps = flowDao.getFlowByMain(handle, order, monthStr) ;
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
