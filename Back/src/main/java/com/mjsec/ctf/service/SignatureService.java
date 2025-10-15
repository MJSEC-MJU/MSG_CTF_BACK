package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.*;
import com.mjsec.ctf.dto.SignatureDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.*;
import com.mjsec.ctf.type.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignatureService {

    private final SignatureCodeRepository codeRepo;
    private final TeamSignatureUnlockRepository unlockRepo;
    private final UserRepository userRepo;
    private final ChallengeRepository challengeRepo;
    private final RedissonClient redisson;
    private final PasswordEncoder passwordEncoder;

    private String currentLoginId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 코드 검증 + 언락(없으면 생성) + 코드 1회성 소비 */
    @Transactional
    public SignatureDto.CheckResponse checkAndUnlock(Long challengeId, SignatureDto.Request req) {
        var ch = challengeRepo.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (ch.getCategory() != com.mjsec.ctf.type.ChallengeCategory.SIGNATURE) {
            throw new RestApiException(ErrorCode.BAD_REQUEST, "시그니처 문제만 인증할 수 있습니다.");
        }

        // 로그인 유저 → 현재 팀만 사용 (팀명/클럽명은 더 이상 받지 않음)
        var loginId = currentLoginId();
        var user = userRepo.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));
        if (user.getCurrentTeamId() == null) throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        Long teamId = user.getCurrentTeamId();

        // 이미 언락되어 있으면 OK
        if (unlockRepo.existsByTeamIdAndChallengeId(teamId, challengeId)) {
            return SignatureDto.CheckResponse.builder()
                    .valid(true).unlocked(true).teamId(teamId).challengeId(challengeId).build();
        }

        String rawCode = req.getSignature().trim();
        String digest  = sha256Hex(rawCode);

        // 코드별 분산락
        String lockKey = "sig:" + challengeId + ":" + digest;
        RLock lock = redisson.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) throw new RestApiException(ErrorCode.BAD_REQUEST, "잠시 후 다시 시도해주세요.");

            var code = codeRepo.findByChallengeIdAndCodeDigest(challengeId, digest)
                    .orElseThrow(() -> new RestApiException(ErrorCode.INVALID_SIGNATURE));

            // 해시 재검증
            if (!passwordEncoder.matches(rawCode, code.getCodeHash()))
                throw new RestApiException(ErrorCode.INVALID_SIGNATURE);

            // 배정된 코드는 해당 팀만 사용 가능
            if (code.getAssignedTeamId() != null && !code.getAssignedTeamId().equals(teamId))
                throw new RestApiException(ErrorCode.INVALID_SIGNATURE);

            // 최초 소비 처리(미소비 상태라면)
            if (!code.isConsumed()) {
                // 미배정 코드면 최초 소비 팀으로 귀속
                if (code.getAssignedTeamId() == null) code.setAssignedTeamId(teamId);
                code.setConsumed(true);
                code.setConsumedAt(LocalDateTime.now());
                codeRepo.save(code);
            }

            // 팀×문제 언락 기록
            if (!unlockRepo.existsByTeamIdAndChallengeId(teamId, challengeId)) {
                unlockRepo.save(TeamSignatureUnlockEntity.builder()
                        .teamId(teamId).challengeId(challengeId)
                        .unlockedAt(LocalDateTime.now()).build());
            }

            return SignatureDto.CheckResponse.builder()
                    .valid(true).unlocked(true).teamId(teamId).challengeId(challengeId).build();

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RestApiException(ErrorCode.BAD_REQUEST, "잠시 후 다시 시도해주세요.");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) lock.unlock();
        }
    }

    public SignatureDto.StatusResponse status(Long challengeId) {
        var ch = challengeRepo.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));
        if (ch.getCategory() != com.mjsec.ctf.type.ChallengeCategory.SIGNATURE) {
            throw new RestApiException(ErrorCode.BAD_REQUEST, "시그니처 문제만 조회할 수 있습니다.");
        }

        var loginId = currentLoginId();
        var user = userRepo.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));
        if (user.getCurrentTeamId() == null) throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);

        boolean unlocked = unlockRepo.existsByTeamIdAndChallengeId(user.getCurrentTeamId(), challengeId);
        return SignatureDto.StatusResponse.builder()
                .unlocked(unlocked).teamId(user.getCurrentTeamId()).challengeId(challengeId).build();
    }
}
