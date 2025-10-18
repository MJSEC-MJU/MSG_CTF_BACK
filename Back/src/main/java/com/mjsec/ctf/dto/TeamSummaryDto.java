package com.mjsec.ctf.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeamSummaryDto {

    private Long teamId;

    private String teamName;

    private int teamTotalPoint;

    private int teamMileage;

    private List<String> memberEmails;
}
