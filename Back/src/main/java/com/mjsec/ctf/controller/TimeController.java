package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.ContestConfigDto;
import com.mjsec.ctf.service.ContestConfigService;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TimeController {

    private final ContestConfigService contestConfigService;

    @Operation(summary = "서버 시간 조회", description = "서버의 현재 시간을 반환합니다.")
    @GetMapping("/api/server-time")
    public Map<String, LocalDateTime> getServerTime() {

        Map<String, LocalDateTime> response = new HashMap<>();
        response.put("serverTime", LocalDateTime.now());
        return response;
    }

    @Operation(summary = "대회 시간 조회", description = "대회 시작/종료 시간과 현재 서버 시간을 반환합니다.")
    @GetMapping("/api/contest-time")
    public ResponseEntity<ContestConfigDto.Response> getContestTime() {
        ContestConfigDto.Response response = contestConfigService.getContestTime();
        return ResponseEntity.ok(response);
    }
}

