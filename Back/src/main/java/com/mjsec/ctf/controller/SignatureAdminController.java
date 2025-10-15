package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.SignatureAdminDto;
import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.service.SignatureAdminService;
import com.mjsec.ctf.type.ResponseMessage;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/signature")
@RequiredArgsConstructor
public class SignatureAdminController {

    private final SignatureAdminService signatureAdminService;

    // ---------- 기본: BULK / IMPORT / EXPORT ----------

    @Operation(
        summary = "시그니처 코드 일괄 업서트 (JSON)",
        description = "teamName, challengeId, code(6자리) 목록을 받아 업서트합니다."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/codes/bulk", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<Object>> upsertCodes(
            @RequestBody @Valid List<SignatureAdminDto.UpsertRequest> requests
    ) {
        int upserted = signatureAdminService.upsertCodes(requests);
        return ResponseEntity.ok(
                SuccessResponse.of(
                        ResponseMessage.SIGNATURE_CODES_UPSERT_SUCCESS,
                        Map.of("upserted", upserted)
                )
        );
    }

    @Operation(
        summary = "시그니처 코드 CSV 임포트",
        description = "CSV 헤더: teamName,challengeId,code"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/codes/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<Object>> importCsv(
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        int imported = signatureAdminService.importCodesCsv(file);
        return ResponseEntity.ok(
                SuccessResponse.of(
                        ResponseMessage.SIGNATURE_CODES_IMPORT_SUCCESS,
                        Map.of("imported", imported)
                )
        );
    }

    @Operation(
        summary = "시그니처 코드 CSV 익스포트",
        description = "CSV 헤더: teamName,challengeId,teamId,codeDigest,consumed"
    )
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/codes/export")
    public ResponseEntity<Resource> exportCsv() {
        byte[] csv = signatureAdminService.exportCodesCsv();
        ByteArrayResource resource = new ByteArrayResource(csv);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(
                ContentDisposition.attachment()
                        .filename(encodeFilename("signature_codes.csv"))
                        .build()
        );

        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
    }

    // ---------- 추가: 풀 조회 / 생성 / 재배정 / 삭제 / 전체삭제 / 강제언락 ----------

    @Operation(summary = "코드 풀 조회(평문 없음)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/codes/pool/{challengeId}")
    public ResponseEntity<SignatureAdminDto.PoolListResponse> pool(@PathVariable Long challengeId) {
        return ResponseEntity.ok(signatureAdminService.listPool(challengeId));
    }

    @Operation(summary = "랜덤 코드 생성(평문 응답)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/codes/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignatureAdminDto.GenerateResponse> generate(
            @RequestBody @Valid SignatureAdminDto.GenerateRequest req
    ) {
        return ResponseEntity.ok(signatureAdminService.generateCodes(req));
    }

    @Operation(summary = "코드 재배정 / 소비상태 초기화")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/codes/reassign", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<Void>> reassign(
            @RequestBody @Valid SignatureAdminDto.ReassignRequest req
    ) {
        signatureAdminService.reassign(req);
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.OK));
    }

    @Operation(summary = "단일 코드 삭제(by codeDigest)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/codes/by-digest")
    public ResponseEntity<SuccessResponse<Void>> deleteByDigest(
            @RequestParam Long challengeId,
            @RequestParam String codeDigest
    ) {
        signatureAdminService.deleteByDigest(challengeId, codeDigest);
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.OK));
    }

    @Operation(summary = "챌린지의 모든 코드 제거(일괄)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/codes/challenge/{challengeId}")
    public ResponseEntity<SuccessResponse<Object>> purge(@PathVariable Long challengeId) {
        long deleted = signatureAdminService.purgeByChallenge(challengeId);
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.OK, Map.of("deleted", deleted)));
    }

    @Operation(summary = "강제 언락(응급용): 코드 없이 팀을 언락 처리")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/unlock/force", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SuccessResponse<Void>> forceUnlock(
            @RequestBody @Valid SignatureAdminDto.ForceUnlockRequest req
    ) {
        signatureAdminService.forceUnlock(req.getTeamName(), req.getChallengeId());
        return ResponseEntity.ok(SuccessResponse.of(ResponseMessage.OK));
    }

    // 파일명 한글 대응 (RFC 5987)
    private String encodeFilename(String filename) {
        return java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
    }
}
