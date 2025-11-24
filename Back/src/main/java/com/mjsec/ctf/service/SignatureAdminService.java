package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.SignatureCodeEntity;
import com.mjsec.ctf.domain.TeamEntity;
import com.mjsec.ctf.domain.TeamSignatureUnlockEntity;
import com.mjsec.ctf.dto.SignatureAdminDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.*;
import com.mjsec.ctf.type.ErrorCode;
import com.mjsec.ctf.type.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignatureAdminService {

    private final SignatureCodeRepository codeRepo;
    private final ChallengeRepository     challengeRepo;
    private final TeamRepository          teamRepo;
    private final UserRepository          userRepo;
    private final TeamSignatureUnlockRepository unlockRepo;
    private final PasswordEncoder         passwordEncoder;

    private String currentLoginId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
    private void assertAdmin() {
        var user = userRepo.findByLoginId(currentLoginId())
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.ROLE_ADMIN) throw new RestApiException(ErrorCode.FORBIDDEN);
    }

    private static String sha256Hex(String s) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String gen6() {
        int n = ThreadLocalRandom.current().nextInt(0, 1_000_000);
        return String.format("%06d", n);
    }

    // ---------- BULK UPSERT ----------
    @Transactional
    public int upsertCodes(List<SignatureAdminDto.UpsertRequest> requests) {
        assertAdmin();
        int count = 0;

        for (var r : requests) {
            // challenge 존재 확인
            challengeRepo.findById(r.getChallengeId())
                    .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

            // teamName → teamId
            var team = teamRepo.findByTeamName(r.getTeamName())
                    .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));
            Long teamId = team.getTeamId();

            String code = r.getCode().trim();
            if (!code.matches("\\d{6}")) {
                throw new RestApiException(ErrorCode.BAD_REQUEST, "코드는 6자리 숫자여야 합니다: " + code);
            }

            String digest = sha256Hex(code);
            var existing = codeRepo.findByChallengeIdAndCodeDigest(r.getChallengeId(), digest);

            if (existing.isPresent()) {
                // ✅ 정상 레코드 존재: 재배정 + 해시 갱신 + 소비 초기화
                var ent = existing.get();
                ent.setAssignedTeamId(teamId);
                ent.setCodeHash(passwordEncoder.encode(code));
                ent.setConsumed(false);
                ent.setConsumedAt(null);
                codeRepo.save(ent);
            } else {
                // ✅ 정상 레코드 없음 → 소프트삭제된 동일 코드가 DB에 남아있는지 확인
                var any = codeRepo.findAnyByChallengeIdAndCodeDigest(r.getChallengeId(), digest);
                if (any.isPresent()) {
                    // 🔄 소프트삭제 행 복구 + 재배정/초기화
                    codeRepo.undeleteAndReset(
                            any.get().getId(),
                            teamId,
                            passwordEncoder.encode(code)
                    );
                } else {
                    // 🆕 완전 신규
                    codeRepo.save(SignatureCodeEntity.builder()
                            .challengeId(r.getChallengeId())
                            .codeDigest(digest)
                            .codeHash(passwordEncoder.encode(code))
                            .assignedTeamId(teamId)   // 고정 배정
                            .consumed(false)
                            .consumedAt(null)
                            .build());
                }
            }
            count++;
        }

        return count;
    }

    // ---------- CSV IMPORT ----------
    // 헤더: teamName,challengeId,code
    @Transactional
    public int importCodesCsv(MultipartFile file) {
        assertAdmin();
        int imported = 0;

        try (var br = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String header = br.readLine();
            if (header == null) return 0;

            String line;
            while ((line = br.readLine()) != null) {
                var parts = line.split(",", -1);
                if (parts.length < 3) continue;

                String teamName  = parts[0].trim();
                Long challengeId = Long.parseLong(parts[1].trim());
                String code      = parts[2].trim();

                var req = new SignatureAdminDto.UpsertRequest();
                req.setTeamName(teamName);
                req.setChallengeId(challengeId);
                req.setCode(code);

                imported += upsertCodes(List.of(req));
            }
        } catch (Exception e) {
            throw new RestApiException(ErrorCode.BAD_REQUEST, "CSV 파싱 실패: " + e.getMessage());
        }
        return imported;
    }

    // ---------- CSV EXPORT ----------
    // 헤더: teamName,challengeId,teamId,codeDigest,consumed
    public byte[] exportCodesCsv() {
        assertAdmin();

        var all = codeRepo.findAll();
        Map<Long, String> teamNameCache = teamRepo.findAll().stream()
                .collect(Collectors.toMap(TeamEntity::getTeamId, TeamEntity::getTeamName));

        var sb = new StringBuilder();
        sb.append("teamName,challengeId,teamId,codeDigest,consumed\n");
        for (var c : all) {
            String teamName = c.getAssignedTeamId() == null ? "" : teamNameCache.getOrDefault(c.getAssignedTeamId(), "");
            sb.append(escape(teamName)).append(',')
              .append(c.getChallengeId()).append(',')
              .append(c.getAssignedTeamId() == null ? "" : c.getAssignedTeamId()).append(',')
              .append(c.getCodeDigest()).append(',')
              .append(c.isConsumed()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ---------- 코드 풀 조회 ----------
    public SignatureAdminDto.PoolListResponse listPool(Long challengeId) {
        assertAdmin();
        challengeRepo.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        var items = codeRepo.findAllByChallengeId(challengeId).stream()
                .map(c -> SignatureAdminDto.PoolItem.builder()
                        .id(c.getId())
                        .codeDigest(c.getCodeDigest())
                        .assignedTeamId(c.getAssignedTeamId())
                        .consumed(c.isConsumed())
                        .consumedAt(c.getConsumedAt())
                        .build())
                .collect(Collectors.toList());

        return SignatureAdminDto.PoolListResponse.builder()
                .challengeId(challengeId)
                .items(items)
                .build();
    }

    // ---------- 랜덤 코드 생성 ----------
    @Transactional
    public SignatureAdminDto.GenerateResponse generateCodes(SignatureAdminDto.GenerateRequest req) {
        assertAdmin();

        var ch = challengeRepo.findById(req.getChallengeId())
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        Long assignTeamId = null;
        if (req.getTeamName() != null && !req.getTeamName().isBlank()) {
            assignTeamId = teamRepo.findByTeamName(req.getTeamName())
                    .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND))
                    .getTeamId();
        }

        int toCreate = req.getCount();
        List<String> plainCodes = new ArrayList<>(toCreate);
        Set<String> digestsInBatch = new HashSet<>();
        int attempts = 0, maxAttempts = toCreate * 30;

        while (plainCodes.size() < toCreate && attempts++ < maxAttempts) {
            String code = gen6();
            String digest = sha256Hex(code);
            if (digestsInBatch.contains(digest)) continue;

            // 기존 활성 + 소프트삭제 모두 충돌 회피
            if (codeRepo.findByChallengeIdAndCodeDigest(ch.getChallengeId(), digest).isPresent()) continue;
            if (codeRepo.findAnyByChallengeIdAndCodeDigest(ch.getChallengeId(), digest).isPresent()) continue;

            codeRepo.save(SignatureCodeEntity.builder()
                    .challengeId(ch.getChallengeId())
                    .codeDigest(digest)
                    .codeHash(passwordEncoder.encode(code))
                    .assignedTeamId(assignTeamId)
                    .consumed(false)
                    .consumedAt(null)
                    .build());

            digestsInBatch.add(digest);
            plainCodes.add(code);
        }

        return SignatureAdminDto.GenerateResponse.builder()
                .challengeId(ch.getChallengeId())
                .assignedTeamId(assignTeamId)
                .created(plainCodes.size())
                .codes(plainCodes)
                .build();
    }

    // ---------- 코드 재배정/소비상태 초기화 ----------
    @Transactional
    public void reassign(SignatureAdminDto.ReassignRequest req) {
        assertAdmin();

        var code = codeRepo.findByChallengeIdAndCodeDigest(req.getChallengeId(), req.getCodeDigest())
                .orElseThrow(() -> new RestApiException(ErrorCode.INVALID_SIGNATURE));

        Long newTeamId = null;
        if (req.getTeamName() != null && !req.getTeamName().isBlank()) {
            newTeamId = teamRepo.findByTeamName(req.getTeamName())
                    .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND))
                    .getTeamId();
        }
        code.setAssignedTeamId(newTeamId);

        if (Boolean.TRUE.equals(req.getResetConsumed())) {
            code.setConsumed(false);
            code.setConsumedAt(null);
        }
        codeRepo.save(code);
    }

    // ---------- 단건 삭제 ----------
    @Transactional
    public void deleteByDigest(Long challengeId, String codeDigest) {
        assertAdmin();
        var code = codeRepo.findByChallengeIdAndCodeDigest(challengeId, codeDigest)
                .orElseThrow(() -> new RestApiException(ErrorCode.INVALID_SIGNATURE));
        // 여기서의 delete(entity)는 @SQLDelete로 소프트삭제 수행
        codeRepo.delete(code);
    }

    // ---------- 챌린지 전체 코드 제거 ----------
    @Transactional
    public long purgeByChallenge(Long challengeId) {
        assertAdmin();
        challengeRepo.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));
        // 파생 deleteByChallengeId는 보통 하드삭제지만, 확실히 하려면 네이티브 메서드 사용
        return codeRepo.hardDeleteByChallengeId(challengeId);
    }

    // ---------- 강제 언락(응급용) ----------
    @Transactional
    public void forceUnlock(String teamName, Long challengeId) {
        assertAdmin();

        var team = teamRepo.findByTeamName(teamName)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));
        Long teamId = team.getTeamId();

        boolean exists = unlockRepo.existsByTeamIdAndChallengeId(teamId, challengeId);
        if (!exists) {
            unlockRepo.save(TeamSignatureUnlockEntity.builder()
                    .teamId(teamId)
                    .challengeId(challengeId)
                    .unlockedAt(LocalDateTime.now())
                    .build());
        }
    }
}
