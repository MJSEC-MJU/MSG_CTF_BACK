package com.mjsec.ctf.controller;

import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.dto.FlagDto;
import com.mjsec.ctf.dto.SuccessResponse;
import com.mjsec.ctf.dto.ChallengeDto.Simple;
import com.mjsec.ctf.service.ChallengeService;
import com.mjsec.ctf.service.JwtService;
import com.mjsec.ctf.service.ThreatDetectionService;
import com.mjsec.ctf.type.ResponseMessage;
import com.mjsec.ctf.util.IPAddressUtil;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.ByteArrayResource;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final JwtService jwtService;
    private final ChallengeService challengeService;
    private final ThreatDetectionService threatDetectionService;

    // /api/challenges, /api/challenges/, /api/challenges/all 모두 이 핸들러로
    @Operation(summary = "모든 문제 조회(호환용)", description = "모든 문제의 id와 points를 반환합니다.")
    @GetMapping({"", "/", "/all"})
    public ResponseEntity<SuccessResponse<Page<ChallengeDto.Simple>>> getAllChallenges(Pageable pageable) {
        Page<Simple> challenges = challengeService.getAllChallengesOrderedById(pageable);
        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.GET_ALL_CHALLENGE_SUCCESS,
                        challenges
                )
        );
    }

    @Operation(summary = "특정 문제 상세 조회", description = "해당 문제 id를 가진 문제의 상세 정보를 반환합니다.")
    @GetMapping("/{challengeId}")
    public ResponseEntity<SuccessResponse<ChallengeDto.Detail>> getDetailChallenge(@PathVariable Long challengeId){
        ChallengeDto.Detail challengeDetail = challengeService.getDetailChallenge(challengeId);
        return ResponseEntity.status(HttpStatus.OK).body(
                SuccessResponse.of(
                        ResponseMessage.GET_CHALLENGE_DETAIL_SUCCESS,
                        challengeDetail
                )
        );
    }

    @Operation(summary = "문제 파일 다운로드", description = "사용자가 문제 파일을 다운로드 받을 수 있습니다.")
    @GetMapping("/{challengeId}/download-file")
    public ResponseEntity<ByteArrayResource> downloadChallengeFile(@PathVariable Long challengeId) throws IOException {
        byte[] data = challengeService.downloadChallengeFile(challengeId);
        ByteArrayResource resource = new ByteArrayResource(data);
        String fileName = "challenge-" + challengeId + ".zip";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentLength(data.length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @Operation(summary = "문제 제출", description = "사용자가 플래그를 제출합니다.")
    @PostMapping("/{challengeId}/submit")
    public ResponseEntity<SuccessResponse<String>> submitChallenge(
            @PathVariable Long challengeId,
            @RequestBody FlagDto flagDto,
            @RequestHeader("Authorization") String authorizationHeader,
            HttpServletRequest request) {

        String token = authorizationHeader.substring(7);
        String loginId = jwtService.getLoginId(token);
        String flag = flagDto.getSubmitFlag();

        String clientIP = IPAddressUtil.getClientIP(request);
        String result = challengeService.submit(loginId, challengeId, flag, clientIP);

        if ("Correct".equals(result)) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    SuccessResponse.of(ResponseMessage.SUBMIT_SUCCESS, result)
            );
        } else if ("Submitted".equals(result)) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    SuccessResponse.of(ResponseMessage.ALREADY_SUBMITTED, result)
            );
        } else if ("Wait".equals(result)) {
            return ResponseEntity.status(HttpStatus.OK).body(
                    SuccessResponse.of(ResponseMessage.SUBMIT_FAILED_WAIT, result)
            );
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(
                    SuccessResponse.of(ResponseMessage.SUBMIT_FAILED_WRONG, result)
            );
        }
    }
}
