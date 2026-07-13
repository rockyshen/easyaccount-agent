package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.dao.AccountDao;
import com.rockyshen.easyaccountagent.dto.AccountResponseDto;
import com.rockyshen.easyaccountagent.entity.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountDao accountDao;

    @Transactional(readOnly = true)
    public List<AccountResponseDto> getAllAccount() {
        List<AccountResponseDto> result = new ArrayList<>();
        for (Account account : accountDao.findByDisableFalse()) {
            AccountResponseDto dto = new AccountResponseDto();
            BeanUtils.copyProperties(account, dto);
            dto.setName(account.getAName());
            result.add(dto);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Account getOriginAccountById(int id) {
        return accountDao.findById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOriginAccount(Account account) {
        accountDao.save(account);
    }

    @Transactional(rollbackFor = Exception.class)
    public Account createAccount(String name, String initialMoney, String card, String note) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("账户名称不能为空");
        }
        Account account = new Account();
        account.setAName(name.trim());
        account.setMoney(formatMoney(initialMoney));
        account.setExemptMoney("0.00");
        account.setCard(card == null ? "" : card.trim());
        account.setNote(note == null ? "" : note.trim());
        account.setDisable(false);
        account.setCreateTime(new Date());
        accountDao.insert(account);
        return account;
    }

    @Transactional(rollbackFor = Exception.class)
    public Account updateAccount(int id, String name, String card, String note, String exemptMoney) {
        Account account = requireActiveAccount(id);
        if (name != null && !name.isBlank()) {
            account.setAName(name.trim());
        }
        if (card != null) {
            account.setCard(card.trim());
        }
        if (note != null) {
            account.setNote(note.trim());
        }
        if (exemptMoney != null && !exemptMoney.isBlank()) {
            account.setExemptMoney(formatMoney(exemptMoney));
        }
        accountDao.update(account);
        return account;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(int id) {
        Account account = requireActiveAccount(id);
        account.setDisable(true);
        accountDao.update(account);
    }

    private Account requireActiveAccount(int id) {
        Account account = accountDao.findById(id);
        if (account == null) {
            throw new IllegalArgumentException("账户 id=" + id + " 不存在");
        }
        if (Boolean.TRUE.equals(account.getDisable())) {
            throw new IllegalArgumentException("账户 id=" + id + " 已停用");
        }
        return account;
    }

    private static String formatMoney(String money) {
        if (money == null || money.isBlank()) {
            return "0.00";
        }
        return new BigDecimal(money.trim()).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
