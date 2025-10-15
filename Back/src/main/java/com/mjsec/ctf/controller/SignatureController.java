package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.SignatureDto;
import com.mjsec.ctf.service.SignatureService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/signature")
@RequiredArgsConstructor
public class SignatureController {

    private final SignatureService signatureService;

    @Operation(summary = "시그니처 검증 + 언락")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{challengeId}/check")
    public ResponseEntity<SignatureDto.CheckResponse> check(
            @PathVariable Long challengeId,
            @RequestBody @Valid SignatureDto.Request req
    ) {
        return ResponseEntity.ok(signatureService.checkAndUnlock(challengeId, req));
    }

    @Operation(summary = "시그니처 언락 상태 조회")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{challengeId}/status")
    public ResponseEntity<SignatureDto.StatusResponse> status(@PathVariable Long challengeId) {
        return ResponseEntity.ok(signatureService.status(challengeId));
    }
}
