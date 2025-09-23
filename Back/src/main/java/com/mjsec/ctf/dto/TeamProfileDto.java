package com.mjsec.ctf.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeamProfileDto {

    private Long teamId;

    private String teamName;

    private String userEmail;

    private List<String> memberEmail;

    private int teamMileage;

    private int teamTotalPoint;

    private int teamSolvedCount;
}
