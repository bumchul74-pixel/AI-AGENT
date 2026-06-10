package com.hanwha.ai.home.controller;

import com.hanwha.ai.home.dto.HomeResponse;
import com.hanwha.ai.home.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/")
    public HomeResponse home() {
        return homeService.getHome();
    }
}
