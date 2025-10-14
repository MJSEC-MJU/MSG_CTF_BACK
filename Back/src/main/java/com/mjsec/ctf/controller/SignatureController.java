package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.SignatureDto;
import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.service.SignatureService;
import com.mjsec.ctf.type.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/signature")
@RequiredArgsConstructor
public class SignatureController {

    private final SignatureService signatureService;

    @Operation(summary = "문제별 시그니처 대조 & 팀 잠금 해제",
               description = "challengeId 기준으로 시그니처 코드 대조 후 현재 팀을 UNLOCK")
    @PostMapping("/{challengeId}/check-code")
    public ResponseEntity<SuccessResponse<SignatureDto.CheckResponse>> checkCode(
            @PathVariable Long challengeId,
            @RequestBody @Valid SignatureDto.Request request) {

        var data = signatureService.checkAndUnlock(challengeId, request);

        if (data.isValid()) {
            return ResponseEntity.ok(
                SuccessResponse.of(ResponseMessage.SIGNATURE_CHECK_SUCCESS, data)
            );
        } else {
            // 정책 불일치 → 400
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                SuccessResponse.of(ResponseMessage.SIGNATURE_CHECK_FAIL, data)
            );
        }
    }

    @Operation(summary = "문제별 팀 잠금 해제 상태", description = "challengeId 기준 UNLOCK 여부 조회")
    @GetMapping("/{challengeId}/status")
    public ResponseEntity<SuccessResponse<SignatureDto.StatusResponse>> status(@PathVariable Long challengeId) {
        var data = signatureService.status(challengeId);
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.OK, data));
    }
}
