package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.dao.AccountDao;
import com.rockyshen.easyaccountagent.dao.FlowDao;
import com.rockyshen.easyaccountagent.dto.HomeDto;
import com.rockyshen.easyaccountagent.entity.Account;
import com.rockyshen.easyaccountagent.entity.FlowYear;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final FlowDao flowDao;
    private final AccountDao accountDao;

    @Transactional(readOnly = true)
    public HomeDto getHomeBean() {
        HomeDto homeDto = new HomeDto();
        setAccountsBean(homeDto);
        setYearlySummary(homeDto, Year.now().getValue());
        return homeDto;
    }

    private void setAccountsBean(HomeDto homeDto) {
        BigDecimal totalAsset = BigDecimal.ZERO;
        BigDecimal exemptAsset = BigDecimal.ZERO;
        List<Account> accounts = accountDao.findByDisableFalse();
        for (Account account : accounts) {
            totalAsset = totalAsset.add(new BigDecimal(account.getMoney()));
            String exemptStr = account.getExemptMoney();
            if (exemptStr == null || exemptStr.isEmpty()) {
                exemptStr = "0";
            }
            exemptAsset = exemptAsset.add(new BigDecimal(exemptStr));
        }
        homeDto.setTotalAsset(totalAsset.toString());
        homeDto.setNetAsset(totalAsset.subtract(exemptAsset).toString());

        NumberFormat nf = NumberFormat.getPercentInstance();
        nf.setMaximumFractionDigits(2);
        List<HomeDto.HomeAccountBean> homeAccounts = new ArrayList<>();
        for (Account account : accounts) {
            HomeDto.HomeAccountBean hab = new HomeDto.HomeAccountBean();
            hab.setId(account.getId());
            hab.setAccountName(account.getAName());
            hab.setAccountAsset(account.getMoney());
            hab.setExemptAsset(account.getExemptMoney());
            hab.setNote(account.getNote());
            if (totalAsset.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal percent = new BigDecimal(account.getMoney()).divide(totalAsset, 3, RoundingMode.HALF_DOWN);
                String percentStr = nf.format(percent.doubleValue());
                hab.setPercent(percentStr.endsWith("%") ? percentStr.substring(0, percentStr.length() - 1) : percentStr);
            } else {
                hab.setPercent("0");
            }
            homeAccounts.add(hab);
        }
        homeDto.setAccounts(homeAccounts);
    }

    private void setYearlySummary(HomeDto homeDto, int year) {
        FlowYear flowYear = flowDao.getYearlySummary(year);
        if (flowYear == null) {
            homeDto.setYearOutCome("0.00");
            homeDto.setYearIncome("0.00");
            homeDto.setYearBalance("0.00");
            return;
        }
        homeDto.setYearOutCome(new BigDecimal(flowYear.getTotalCosts()).setScale(2, RoundingMode.HALF_UP).toString());
        homeDto.setYearIncome(new BigDecimal(flowYear.getTotalEarns()).setScale(2, RoundingMode.HALF_UP).toString());
        homeDto.setYearBalance(new BigDecimal(flowYear.getTotalBalance()).setScale(2, RoundingMode.HALF_UP).toString());
    }
}
