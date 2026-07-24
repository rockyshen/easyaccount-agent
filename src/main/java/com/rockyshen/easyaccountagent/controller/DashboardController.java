package com.rockyshen.easyaccountagent.controller;

import com.rockyshen.easyaccountagent.dto.HomeDto;
import com.rockyshen.easyaccountagent.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final HomeService homeService;

    @GetMapping
    public HomeDto dashboard() {
        return homeService.getHomeBean();
    }
}
