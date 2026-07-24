package com.rockyshen.easyaccountagent.controller;

import com.rockyshen.easyaccountagent.dto.TypeListResponseDto;
import com.rockyshen.easyaccountagent.service.TypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/types")
@RequiredArgsConstructor
public class TypeController {

    private final TypeService typeService;

    @GetMapping
    public ResponseEntity<?> listByAction(@RequestParam(required = false) Integer actionId) {
        if (actionId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "actionId 不能为空"));
        }
        List<TypeListResponseDto> types = typeService.queryTypeByActionId(actionId);
        return ResponseEntity.ok(types);
    }
}
