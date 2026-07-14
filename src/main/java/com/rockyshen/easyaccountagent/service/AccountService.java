package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.constant.ContentValues;
import com.rockyshen.easyaccountagent.dao.AccountDao;
import com.rockyshen.easyaccountagent.dto.AccountResponseDto;
import com.rockyshen.easyaccountagent.entity.Account;
import lombok.RequiredArgsConstructor;
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
            result.add(new AccountResponseDto().convertToDto(account));
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

    /**
     * @param accountType 0=普通账户，1=信用卡
     * @param initialMoney 普通账户为初始余额；信用卡为信用额度
     */
    @Transactional(rollbackFor = Exception.class)
    public Account createAccount(String name, String initialMoney, String card, String note, int accountType) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("账户名称不能为空");
        }
        if (accountType != ContentValues.ACCOUNT_TYPE_NORMAL && accountType != ContentValues.ACCOUNT_TYPE_CREDIT) {
            throw new IllegalArgumentException("账户类型无效，仅支持 0=普通 或 1=信用卡");
        }

        Account account = new Account();
        account.setAName(name.trim());
        account.setCard(card == null ? "" : card.trim());
        account.setNote(note == null ? "" : note.trim());
        account.setDisable(false);
        account.setCreateTime(new Date());
        account.setAccountType(accountType);

        String amount = formatMoney(initialMoney);
        if (accountType == ContentValues.ACCOUNT_TYPE_CREDIT) {
            if (new BigDecimal(amount).compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("信用卡信用额度必须大于 0");
            }
            // money=可用额度，exemptMoney=信用额度；新建时已用为 0
            account.setMoney(amount);
            account.setExemptMoney(amount);
        } else {
            account.setMoney(amount);
            account.setExemptMoney("0.00");
        }

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
            if (isCreditAccount(account)) {
                adjustCreditLimit(account, formatMoney(exemptMoney));
            } else {
                account.setExemptMoney(formatMoney(exemptMoney));
            }
        }
        if (account.getAccountType() == null) {
            account.setAccountType(ContentValues.ACCOUNT_TYPE_NORMAL);
        }
        accountDao.update(account);
        return account;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(int id) {
        Account account = requireActiveAccount(id);
        account.setDisable(true);
        if (account.getAccountType() == null) {
            account.setAccountType(ContentValues.ACCOUNT_TYPE_NORMAL);
        }
        accountDao.update(account);
    }

    public static boolean isCreditAccount(Account account) {
        return account != null
                && account.getAccountType() != null
                && account.getAccountType() == ContentValues.ACCOUNT_TYPE_CREDIT;
    }

    /** 调整信用额度时保持已用额度不变：newAvailable = newLimit - owed */
    private static void adjustCreditLimit(Account account, String newLimitStr) {
        BigDecimal newLimit = new BigDecimal(newLimitStr);
        if (newLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("信用卡信用额度必须大于 0");
        }
        BigDecimal available = new BigDecimal(nullToZero(account.getMoney()));
        BigDecimal oldLimit = new BigDecimal(nullToZero(account.getExemptMoney()));
        BigDecimal owed = oldLimit.subtract(available);
        if (owed.compareTo(BigDecimal.ZERO) < 0) {
            owed = BigDecimal.ZERO;
        }
        if (newLimit.compareTo(owed) < 0) {
            throw new IllegalArgumentException("新信用额度不能小于已用额度 " + owed.toPlainString());
        }
        account.setExemptMoney(newLimit.setScale(2, RoundingMode.HALF_UP).toPlainString());
        account.setMoney(newLimit.subtract(owed).setScale(2, RoundingMode.HALF_UP).toPlainString());
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

    private static String nullToZero(String value) {
        return value == null || value.isBlank() ? "0" : value;
    }
}
