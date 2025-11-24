package com.mjsec.ctf.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSolveRecordDto {
    private Long historyId;
    private Long challengeId;
    private String challengeTitle;
    private String loginId;
    private String teamName;
    private Long teamId;
    private String univ;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime solvedTime;
    private int pointsAwarded;  // 당시 획득한 점수
    private int mileageAwarded; // 당시 획득한 마일리지 (기본)
    private int mileageBonus;   // 퍼스트 블러드 보너스 마일리지
    private boolean isFirstBlood; // 퍼스트 블러드 여부
}
