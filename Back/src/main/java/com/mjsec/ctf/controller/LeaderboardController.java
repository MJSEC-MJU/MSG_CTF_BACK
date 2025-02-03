package com.mjsec.ctf.controller;

import com.mjsec.ctf.entity.Leaderboard;
import com.mjsec.ctf.service.LeaderboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/leaderboard")
public class LeaderboardController {
    private final LeaderboardService leaderboardService;


    // 이게 원래 없어도 떠야 되던데 페이지가 안떠서 GetMapping 추가해서 넣었습니다.
    @GetMapping
    public String showLeaderboard() {
        return "leaderboard";
    }

    @Autowired
    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    // stream 경로에서 json 데이터 확인 (글자 깨지는 경우가 종종 있음)
    @GetMapping("/stream")
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); //접속 중에는 sse 연결 timeout 제한 없음
        new Thread(() -> {
            try {
                while (true) {
                    List<Leaderboard> leaderboardEntities = leaderboardService.getLeaderboard();
                    emitter.send(leaderboardEntities, MediaType.APPLICATION_JSON);
                    Thread.sleep(5000); //5초로 설정$
                }
            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        }).start();
        return emitter;
    }
}
