package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.HistoryDto;
import com.mjsec.ctf.entity.Leaderboard;
import com.mjsec.ctf.service.HistoryService;
import com.mjsec.ctf.service.LeaderboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/api/leaderboard")
public class LeaderboardController {
    private final LeaderboardService leaderboardService;
    private final HistoryService historyService;

    @Autowired
    public LeaderboardController(LeaderboardService leaderboardService, HistoryService historyService) {
        this.leaderboardService = leaderboardService;
        this.historyService = historyService;
    }

    // 기존 /stream 엔드포인트 (SSE 통신)
    @CrossOrigin(origins = "*")
    @GetMapping("/stream")
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // 타임아웃 제한 없음
        new Thread(() -> {
            try {
                while (true) {
                    List<Leaderboard> leaderboardEntities = leaderboardService.getLeaderboard();
                    emitter.send(leaderboardEntities, MediaType.APPLICATION_JSON);
                    Thread.sleep(5000); // 5초마다 업데이트
                }
            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }
    
    // /graph 엔드포인트: HistoryDto 데이터를 SSE로 전송
    @CrossOrigin(origins = "*")
    @GetMapping(value = "/graph", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // 타임아웃 제한 없음
        new Thread(() -> {
            try {
                while (true) {
                    List<HistoryDto> historyDtos = historyService.getHistoryDtos();
                    emitter.send(SseEmitter.event().name("update")
                            .data(historyDtos, MediaType.APPLICATION_JSON));
                    Thread.sleep(5000); // 5초마다 업데이트
                }
            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }
}
