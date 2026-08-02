package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.auth.AuthContext;
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
import java.time.LocalDate;
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
        LocalDate today = LocalDate.now();
        setMonthlySummary(homeDto, today.getYear(), today.getMonthValue());
        setYearlySummary(homeDto, Year.now().getValue());
        return homeDto;
    }

    private void setAccountsBean(HomeDto homeDto) {
        int userId = AuthContext.requireUserId();
        BigDecimal totalAsset = BigDecimal.ZERO;
        BigDecimal exemptAsset = BigDecimal.ZERO;
        List<Account> accounts = accountDao.findByDisableFalse(userId);
        for (Account account : accounts) {
            BigDecimal money = new BigDecimal(nullToZero(account.getMoney()));
            if (AccountService.isCreditAccount(account)) {
                BigDecimal limit = new BigDecimal(nullToZero(account.getExemptMoney()));
                BigDecimal used = limit.subtract(money).max(BigDecimal.ZERO);
                exemptAsset = exemptAsset.add(used);
            } else {
                totalAsset = totalAsset.add(money);
                String exemptStr = account.getExemptMoney();
                if (exemptStr == null || exemptStr.isEmpty()) {
                    exemptStr = "0";
                }
                exemptAsset = exemptAsset.add(new BigDecimal(exemptStr));
            }
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
            hab.setNote(account.getNote());
            if (AccountService.isCreditAccount(account)) {
                BigDecimal limit = new BigDecimal(nullToZero(account.getExemptMoney()));
                BigDecimal available = new BigDecimal(nullToZero(account.getMoney()));
                BigDecimal used = limit.subtract(available).max(BigDecimal.ZERO);
                hab.setAccountAsset(available.toPlainString());
                hab.setExemptAsset(used.toPlainString());
                hab.setAccountName(account.getAName() + "(信用卡)");
                hab.setPercent("0");
            } else {
                hab.setAccountAsset(account.getMoney());
                hab.setExemptAsset(account.getExemptMoney());
                if (totalAsset.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal percent = new BigDecimal(account.getMoney()).divide(totalAsset, 3, RoundingMode.HALF_DOWN);
                    String percentStr = nf.format(percent.doubleValue());
                    hab.setPercent(percentStr.endsWith("%") ? percentStr.substring(0, percentStr.length() - 1) : percentStr);
                } else {
                    hab.setPercent("0");
                }
            }
            homeAccounts.add(hab);
        }
        homeDto.setAccounts(homeAccounts);
    }

    private static String nullToZero(String value) {
        return value == null || value.isBlank() ? "0" : value;
    }

    private void setMonthlySummary(HomeDto homeDto, int year, int month) {
        FlowYear flowMonth = flowDao.getMonthlySummary(year, month, AuthContext.requireUserId());
        if (flowMonth == null) {
            homeDto.setCurOutCome("0.00");
            homeDto.setCurIncome("0.00");
            homeDto.setCurBalance("0.00");
            return;
        }
        homeDto.setCurOutCome(scaleMoney(flowMonth.getTotalCosts()));
        homeDto.setCurIncome(scaleMoney(flowMonth.getTotalEarns()));
        homeDto.setCurBalance(scaleMoney(flowMonth.getTotalBalance()));
    }

    private void setYearlySummary(HomeDto homeDto, int year) {
        FlowYear flowYear = flowDao.getYearlySummary(year, AuthContext.requireUserId());
        if (flowYear == null) {
            homeDto.setYearOutCome("0.00");
            homeDto.setYearIncome("0.00");
            homeDto.setYearBalance("0.00");
            return;
        }
        homeDto.setYearOutCome(scaleMoney(flowYear.getTotalCosts()));
        homeDto.setYearIncome(scaleMoney(flowYear.getTotalEarns()));
        homeDto.setYearBalance(scaleMoney(flowYear.getTotalBalance()));
    }

    private static String scaleMoney(String value) {
        return new BigDecimal(nullToZero(value)).setScale(2, RoundingMode.HALF_UP).toString();
    }
}
