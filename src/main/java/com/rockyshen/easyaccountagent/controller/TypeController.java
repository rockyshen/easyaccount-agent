package com.rockyshen.easyaccountagent.controller;

import com.rockyshen.easyaccountagent.dto.CreateTypeRequestDto;
import com.rockyshen.easyaccountagent.dto.TypeListResponseDto;
import com.rockyshen.easyaccountagent.dto.UpdateTypeRequestDto;
import com.rockyshen.easyaccountagent.entity.Type;
import com.rockyshen.easyaccountagent.service.TypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

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

    /** 推荐路径，与账户 API 风格一致 */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateTypeRequestDto body) {
        return createInternal(body);
    }

    /** iOS 过渡路径，与 POST /api/types 同源 */
    @PostMapping("/create")
    public ResponseEntity<?> createLegacy(@RequestBody CreateTypeRequestDto body) {
        return createInternal(body);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id, @RequestBody UpdateTypeRequestDto body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "请求体不能为空"));
        }
        try {
            Type updated = typeService.updateType(id, body.getTName(), body.getActionId(), body.getParent());
            return ResponseEntity.ok(TypeListResponseDto.fromEntity(updated));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        try {
            typeService.deleteType(id);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private ResponseEntity<?> createInternal(CreateTypeRequestDto body) {
        if (body == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "请求体不能为空"));
        }
        try {
            Type created = typeService.createType(body.getTName(), body.getActionId(), body.getParent());
            return ResponseEntity.ok(TypeListResponseDto.fromEntity(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
