package com.rockyshen.easyaccountagent.controller;

import com.rockyshen.easyaccountagent.dto.AccountResponseDto;
import com.rockyshen.easyaccountagent.dto.CreateAccountRequestDto;
import com.rockyshen.easyaccountagent.dto.UpdateAccountRequestDto;
import com.rockyshen.easyaccountagent.entity.Account;
import com.rockyshen.easyaccountagent.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public List<AccountResponseDto> list() {
        return accountService.getAllAccount();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateAccountRequestDto body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "请求体不能为空"));
        }
        if (body.getAccountType() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "accountType 不能为空"));
        }
        try {
            Account created = accountService.createAccount(
                    body.getName(),
                    body.getInitialMoney(),
                    body.getCard(),
                    body.getNote(),
                    body.getAccountType());
            return ResponseEntity.ok(new AccountResponseDto().convertToDto(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody UpdateAccountRequestDto body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "请求体不能为空"));
        }
        try {
            Account updated = accountService.updateAccount(
                    id, body.getName(), body.getCard(), body.getNote(), body.getExemptMoney());
            return ResponseEntity.ok(new AccountResponseDto().convertToDto(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        try {
            accountService.deleteAccount(id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
