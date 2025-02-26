package com.mjsec.ctf.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SolvedChallengeDTO {
    private String userId;
    private String challengeId;
    private LocalDateTime solvedTime;
    private int currentScore;
}
