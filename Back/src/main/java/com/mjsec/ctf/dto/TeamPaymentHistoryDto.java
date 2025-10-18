package com.mjsec.ctf.dto;

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
public class TeamPaymentHistoryDto {
    private Long teamPaymentHistoryId;
    private Long teamId;
    private String teamName;
    private Long requesterUserId;
    private String requesterLoginId;
    private int mileageUsed;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    private LocalDateTime createdAt;
}
