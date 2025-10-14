package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.SignatureDto;
import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.service.SignatureService;
import com.mjsec.ctf.type.ResponseMessage;
import com.mjsec.ctf.type.ErrorCode;
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
            // ✅ 성공 시: 프로젝트에 존재하는 메시지 사용
            return ResponseEntity.ok(
                SuccessResponse.of(ResponseMessage.SIGNATURE_CHECK_SUCCESS, data)
            );
        } else {
            // ❌ 실패 시: 존재하지 않는 SIGNATURE_CHECK_FAIL 대신 예외 던짐
            throw new RestApiException(ErrorCode.INVALID_SIGNATURE);
        }
    }

    @Operation(summary = "문제별 팀 잠금 해제 상태", description = "challengeId 기준 UNLOCK 여부 조회")
    @GetMapping("/{challengeId}/status")
    public ResponseEntity<SuccessResponse<SignatureDto.StatusResponse>> status(@PathVariable Long challengeId) {
        var data = signatureService.status(challengeId);
        // ✅ OK 상수 대신 존재하는 메시지 재사용
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.SIGNATURE_CHECK_SUCCESS, data));
    }
}
