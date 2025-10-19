package com.mjsec.ctf.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamHistoryDto {
    private Long teamId;
    private String teamName;
    private String challengeId;
    private String title;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime solvedTime;
    private int currentScore;
    private String solvedBy;  // 풀이한 팀원 loginId

    // 기존 생성자 호환성을 위한 생성자 (solvedBy 없이)
    public TeamHistoryDto(Long teamId, String teamName, String challengeId, String title,
                          LocalDateTime solvedTime, int currentScore) {
        this.teamId = teamId;
        this.teamName = teamName;
        this.challengeId = challengeId;
        this.title = title;
        this.solvedTime = solvedTime;
        this.currentScore = currentScore;
        this.solvedBy = null;
    }
}
