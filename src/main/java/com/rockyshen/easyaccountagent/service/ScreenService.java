package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.auth.AuthContext;
import com.rockyshen.easyaccountagent.dao.FlowDao;
import com.rockyshen.easyaccountagent.dto.FlowListDto;
import com.rockyshen.easyaccountagent.dto.ScreenFlowRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ScreenService {

    private final FlowDao flowDao;

    @Transactional(readOnly = true)
    public FlowListDto getFlowByScreen(ScreenFlowRequestDto getBean) {
        List<Map<String, Object>> maps = flowDao.getFlowByScreen(
                getBean.getChooseHandle(),
                getBean.getAccountId(),
                getBean.getStartDate().trim(),
                getBean.getEndDate().trim(),
                getBean.isSingleMonth(),
                getBean.isCollect(),
                getBean.getNote(),
                AuthContext.requireUserId());
        FlowListDto baseBean = new FlowListDto();
        baseBean.setFlows(new ArrayList<>());
        BigDecimal moneyIn = BigDecimal.ZERO;
        BigDecimal moneyOut = BigDecimal.ZERO;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (Map<String, Object> map : maps) {
            int typeId = (int) map.get("typeId");
            int parentTypeId = map.get("parentTypeId") == null ? -1 : (int) map.get("parentTypeId");
            int actionId = (int) map.get("actionId");
            boolean currentDataCanUse = !getBean.useTypeScreen() && !getBean.useActionScreen()
                    || getBean.useTypeScreen() && !getBean.useActionScreen() && (getBean.getTypes().contains(typeId) || getBean.getTypes().contains(parentTypeId))
                    || !getBean.useTypeScreen() && getBean.useActionScreen() && getBean.getActions().contains(actionId)
                    || getBean.useTypeScreen() && getBean.useActionScreen()
                    && (getBean.getTypes().contains(typeId) || getBean.getTypes().contains(parentTypeId))
                    && getBean.getActions().contains(actionId);
            if (!currentDataCanUse) {
                continue;
            }
            FlowListDto.FlowListSingleDto innerBean = new FlowListDto.FlowListSingleDto();
            innerBean.setId((Integer) map.get("id"));
            innerBean.setMoney((String) map.get("money"));
            innerBean.setExempt((Boolean) map.get("exempt"));
            innerBean.setCollect((Boolean) map.get("collect"));
            innerBean.setHandle((Integer) map.get("handle"));
            innerBean.setHName((String) map.get("handleName"));
            innerBean.setNote((String) map.get("note"));
            Date fDate = (Date) map.get("flowDate");
            innerBean.setFDate(sdf.format(fDate));
            innerBean.setAName((String) map.get("accountName"));
            innerBean.setToAName((String) map.get("toAccountName"));
            if (map.get("parentTypeName") != null) {
                innerBean.setTName(map.get("parentTypeName") + "/" + map.get("typeName"));
            } else {
                innerBean.setTName((String) map.get("typeName"));
            }
            baseBean.getFlows().add(innerBean);
            if (innerBean.getHandle() == 1) {
                moneyOut = moneyOut.add(new BigDecimal(innerBean.getMoney()));
            } else if (innerBean.getHandle() == 0) {
                moneyIn = moneyIn.add(new BigDecimal(innerBean.getMoney()));
            }
        }
        baseBean.setTotalIn(moneyIn.toString());
        baseBean.setTotalOut(moneyOut.toString());
        baseBean.setTotalEarn(moneyIn.subtract(moneyOut).toString());
        return baseBean;
    }
}
