package com.mjsec.ctf.controller;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.dto.AdminSolveRecordDto;
import com.mjsec.ctf.repository.ChallengeRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.mjsec.ctf.dto.ChallengeDto;
import com.mjsec.ctf.service.ChallengeService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/challenge")
@RequiredArgsConstructor
public class AdminChallengeController {
    private final ChallengeService challengeService;
    private final ChallengeRepository challengeRepository;

    @Operation(summary = "전체 문제 요약 조회", description = "관리자 권한으로 전체 문제 목록에서 문제 번호, 제목, 포인트, 카테고리를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/summary")
    public ResponseEntity<List<Map<String, Object>>> getChallengeSummary() {
        List<ChallengeEntity> challenges = challengeRepository.findAllByOrderByChallengeIdAsc(Pageable.unpaged()).getContent();
        List<Map<String, Object>> summaryList = challenges.stream().map(challenge -> {
            Map<String, Object> map = new HashMap<>();
            map.put("challengeId", challenge.getChallengeId());
            map.put("title", challenge.getTitle());
            map.put("points", challenge.getPoints());
            map.put("category", challenge.getCategory().toString());
            map.put("mileage", challenge.getMileage());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(summaryList);
    }
    @Operation(summary = "문제 상세 조회", description = "관리자 권한으로 특정 문제의 상세 정보를 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{challengeId}")
    public ResponseEntity<ChallengeDto.Detail> getChallengeDetail(@PathVariable Long challengeId) {
        ChallengeDto.Detail detail = challengeService.getDetailChallenge(challengeId);
        return ResponseEntity.ok(detail);
    }

    @Operation(summary = "전체 제출 기록 조회", description = "관리자 권한으로 모든 문제의 제출 기록을 조회합니다. 시간순 정렬, 퍼스트 블러드 정보 포함.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/solve-records")
    public ResponseEntity<List<AdminSolveRecordDto>> getAllSolveRecords() {
        List<AdminSolveRecordDto> records = challengeService.getAllSolveRecords();
        return ResponseEntity.ok(records);
    }

    @Operation(summary = "문제별 제출 기록 조회", description = "관리자 권한으로 특정 문제의 모든 제출 기록을 조회합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{challengeId}/solve-records")
    public ResponseEntity<List<AdminSolveRecordDto>> getSolveRecords(@PathVariable Long challengeId) {
        List<AdminSolveRecordDto> records = challengeService.getSolveRecordsByChallenge(challengeId);
        return ResponseEntity.ok(records);
    }

    @Operation(summary = "제출 기록 철회", description = "관리자 권한으로 특정 사용자의 문제 제출 기록을 철회합니다. 점수, 마일리지 반환 및 다이나믹 스코어 재계산이 이루어집니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{challengeId}/solve-records/{loginId}")
    public ResponseEntity<Map<String, String>> revokeSolveRecord(
            @PathVariable Long challengeId,
            @PathVariable String loginId) {
        challengeService.revokeSolveRecord(challengeId, loginId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "제출 기록이 성공적으로 철회되었습니다.");
        return ResponseEntity.ok(response);
    }

}
