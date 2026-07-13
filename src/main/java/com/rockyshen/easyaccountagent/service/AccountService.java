package com.rockyshen.easyaccountagent.service;

import com.rockyshen.easyaccountagent.dao.AccountDao;
import com.rockyshen.easyaccountagent.dto.AccountResponseDto;
import com.rockyshen.easyaccountagent.entity.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
}
