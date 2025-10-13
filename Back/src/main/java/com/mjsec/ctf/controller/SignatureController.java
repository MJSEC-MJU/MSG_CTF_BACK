package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.dto.SignatureDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.service.SignatureService;
import com.mjsec.ctf.type.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.mjsec.ctf.type.ErrorCode;

@Slf4j
@RestController
@RequestMapping("/api/signature")
@RequiredArgsConstructor
public class SignatureController {

    private final SignatureService signatureService;

    @Operation(summary = "시그니처 대조", description = "시그니처 코드 대조")
    @PostMapping("/check-code")
    public ResponseEntity<SuccessResponse<SignatureDto.CheckResponse>> checkCode(
            @RequestBody @Valid SignatureDto.Request request) {

        var data = signatureService.checkCode(request);

        return ResponseEntity.ok(
                SuccessResponse.of(ResponseMessage.SIGNATURE_CHECK_SUCCESS, data)
        );
    }

    @Operation(summary = "시그니처 삽입", description = "시그니처 코드 삽입")
    @PostMapping("/insert-code")
    public ResponseEntity<String> insertCode(@RequestBody @Valid SignatureDto.Request request) {

        var data = signatureService.insertCode(request); // result=true/false, id 반환

        if (data.isResult()) {
            // 생성 의미를 살리려면 201, verify 스타일과 완전 동일하게 하려면 200으로 바꿔도 됨
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("시그니처가 등록되었습니다. (id=" + data.getId() + ")");
        } else {
            // 중복 → 예외 던짐 (글로벌 예외 핸들러가 409 등으로 변환)
            throw new RestApiException(ErrorCode.SIGNATURE_DUPLICATE);
        }
    }
}
