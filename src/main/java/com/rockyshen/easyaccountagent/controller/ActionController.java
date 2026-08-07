package com.rockyshen.easyaccountagent.controller;

import com.rockyshen.easyaccountagent.entity.Action;
import com.rockyshen.easyaccountagent.service.ActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionController {

    private final ActionService actionService;

    /**
     * 仅返回收入 / 支出 / 内部转账。变动极少，允许客户端长期缓存。
     */
    @GetMapping
    public ResponseEntity<List<Action>> list() {
        List<Action> actions = actionService.getActions();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate().mustRevalidate())
                .header("X-Actions-Scope", "primary")
                .body(actions);
    }
}
