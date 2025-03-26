package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.HistoryDto;
import com.mjsec.ctf.domain.LeaderboardEntity;
import com.mjsec.ctf.service.HistoryService;
import com.mjsec.ctf.service.LeaderboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final HistoryService historyService;
    private static final Logger logger = LoggerFactory.getLogger(LeaderboardController.class);

    // 전역 스케줄러: 스레드풀 크기는 예상되는 동시 접속 클라이언트 수에 따라 조정 필요
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    public LeaderboardController(LeaderboardService leaderboardService, HistoryService historyService) {
        this.leaderboardService = leaderboardService;
        this.historyService = historyService;
    }

    /**
     * SSE emitter에 대해 공통 스케줄링 작업을 등록합니다.
     *
     * @param emitter SSE emitter
     * @param task    5초마다 실행할 작업
     * @return 등록된 emitter
     */
    private SseEmitter scheduleSseTask(SseEmitter emitter, Runnable task) {
        final ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(task, 0, 5, TimeUnit.SECONDS);

        emitter.onCompletion(() -> {
            future.cancel(true);
            logger.info("SSE emitter completed");
        });
        emitter.onTimeout(() -> {
            emitter.complete();
            future.cancel(true);
            logger.info("SSE emitter timed out");
        });

        return emitter;
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/stream")
    public SseEmitter stream() {
        // 1시간(3600000ms) 타임아웃 설정
        SseEmitter emitter = new SseEmitter(3600000L);

        scheduleSseTask(emitter, () -> {
            try {
                List<Leaderboard> leaderboardEntities = leaderboardService.getLeaderboard();
                emitter.send(leaderboardEntities, MediaType.APPLICATION_JSON);
            } catch (IOException ex) {
                logger.error("Error sending leaderboard SSE event", ex);
                emitter.complete();
            } catch (Exception ex) {
                logger.error("Unexpected error during leaderboard SSE event", ex);
                while (true) {
                    List<LeaderboardEntity> leaderboardEntityEntities = leaderboardService.getLeaderboard();
                    emitter.send(leaderboardEntityEntities, MediaType.APPLICATION_JSON);
                    Thread.sleep(5000); // 5초마다 업데이트
                }
            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @CrossOrigin(origins = "*")
    @GetMapping(value = "/graph", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter graph() {
        SseEmitter emitter = new SseEmitter(3600000L);

        scheduleSseTask(emitter, () -> {
            try {
                List<HistoryDto> historyDtos = historyService.getHistoryDtos();
                emitter.send(
                        SseEmitter.event()
                                .name("update")
                                .data(historyDtos, MediaType.APPLICATION_JSON)
                );
            } catch (IOException ex) {
                logger.error("Error sending history SSE event", ex);
                emitter.complete();
            } catch (Exception ex) {
                logger.error("Unexpected error during history SSE event", ex);
            }
        });
        return emitter;
    }
}
