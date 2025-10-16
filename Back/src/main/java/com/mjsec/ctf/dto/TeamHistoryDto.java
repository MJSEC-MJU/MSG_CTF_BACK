package com.mjsec.ctf.dto;

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
    private LocalDateTime solvedTime;
    private int currentScore;

}
