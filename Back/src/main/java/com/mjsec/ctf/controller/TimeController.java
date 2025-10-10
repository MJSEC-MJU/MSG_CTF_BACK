package com.mjsec.ctf.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TimeController {

    @GetMapping("/api/server-time")
    public Map<String, LocalDateTime> getServerTime() {

        Map<String, LocalDateTime> response = new HashMap<>();
        response.put("serverTime", LocalDateTime.now());
        return response;
    }
}

