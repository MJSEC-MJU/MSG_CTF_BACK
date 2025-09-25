package com.mjsec.ctf.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentTokenDto {

    private Long paymentTokenId;

    private String token;

    private String loginId;

    private LocalDateTime expiry;

    private LocalDateTime createdAt;
}
