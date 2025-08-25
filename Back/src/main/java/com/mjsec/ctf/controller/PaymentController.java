package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.PaymentTokenDto;
import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.service.JwtService;
import com.mjsec.ctf.service.PaymentService;
import com.mjsec.ctf.type.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final JwtService jwtService;
    private final PaymentService paymentService;

    @Operation(summary = "QR 발급을 위한 토큰 반환", description = "마일리지 결제를 위한 QR 코드 생성을 위해 토큰을 반환합니다.")
    @PostMapping("/qr-token")
    public ResponseEntity<SuccessResponse<PaymentTokenDto>> createPaymentToken(@RequestHeader("Authorization") String authHeader) {

        String token = authHeader.substring(7);
        String loginId = jwtService.getLoginId(token);

        PaymentTokenDto paymentTokenDto = paymentService.createPaymentToken(loginId);

        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.GENERATE_QR_TOKEN_SUCCESS,
                        paymentTokenDto
                )
        );
    }

    @PostMapping("/checkout")
    public ResponseEntity<SuccessResponse<Void>> checkout(
            @RequestParam String paymentToken,
            @RequestParam int mileageUsed
    ) {

        paymentService.processPayment(paymentToken, mileageUsed);

        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.MILEAGE_BASED_CHECKOUT_SUCCESS
                )
        );
    }
}
