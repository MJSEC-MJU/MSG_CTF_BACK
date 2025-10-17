package com.mjsec.ctf.dto;

// 팀 단위용 리더보드 개편 (LeadboardEntity가 하는 로직을 여기서 진행)
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamLeaderboardDto {
    private Long teamId;
    private String teamName;
    private int totalPoint;
    private int solvedCount;  // 풀이한 문제 수
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime lastSolvedTime;
    private int rank;
}