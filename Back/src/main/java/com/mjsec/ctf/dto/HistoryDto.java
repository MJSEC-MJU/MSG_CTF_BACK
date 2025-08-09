package com.mjsec.ctf.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class HistoryDto {
    private String loginId;
    private String challengeId;
    private String title;
    private LocalDateTime solvedTime;
    private int currentScore;

    private String univ;
}
