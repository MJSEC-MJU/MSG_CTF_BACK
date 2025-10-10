package com.mjsec.ctf.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeamSummaryDto {

    private String teamName;

    private int teamTotalPoint;

    private int teamMileage;
}
