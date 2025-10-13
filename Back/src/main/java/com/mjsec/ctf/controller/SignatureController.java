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

        if (data.isResult()){
            return ResponseEntity.status(HttpStatus.OK).body(
                    SuccessResponse.of(
                            ResponseMessage.SIGNATURE_CHECK_SUCCESS
                    )
            );
        } else {
            throw new RestApiException(ErrorCode.INVALID_SIGNATURE);
        }
    }

    @Operation(summary = "시그니처 삽입", description = "시그니처 코드 삽입")
    @PostMapping("/insert-code")
    public ResponseEntity<SuccessResponse<SignatureDto.InsertResponse>> insertCode(
            @RequestBody @Valid SignatureDto.Request request) {

        var data = signatureService.insertCode(request); // result=true/false, id 반환

        if (data.isResult()) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    SuccessResponse.of(
                            ResponseMessage.SIGNATURE_INSERT_SUCCESS
                    )
            );
        } else {
            throw new RestApiException(ErrorCode.SIGNATURE_DUPLICATE);
        }
    }
}
