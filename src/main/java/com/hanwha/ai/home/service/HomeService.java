package com.hanwha.ai.home.service;

import com.hanwha.ai.home.dto.HomeResponse;
import org.springframework.stereotype.Service;

@Service
public class HomeService {
    public HomeResponse getHome() {
        return new HomeResponse("AIP (AI Integration Platform) Spring Boot is running.");
    }
}
