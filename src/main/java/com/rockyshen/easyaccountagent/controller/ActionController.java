package com.rockyshen.easyaccountagent.controller;

import com.rockyshen.easyaccountagent.entity.Action;
import com.rockyshen.easyaccountagent.service.ActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/actions")
@RequiredArgsConstructor
public class ActionController {

    private final ActionService actionService;

    @GetMapping
    public List<Action> list() {
        return actionService.getActions();
    }
}
