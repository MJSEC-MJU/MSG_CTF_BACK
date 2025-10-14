package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeSignaturePolicy;
import com.mjsec.ctf.domain.TeamSignatureUnlockEntity;
import com.mjsec.ctf.dto.SignatureDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.ChallengeSignaturePolicyRepository;
import com.mjsec.ctf.repository.TeamSignatureUnlockRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.type.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignatureService {

    private final ChallengeSignaturePolicyRepository policyRepo;
    private final TeamSignatureUnlockRepository unlockRepo;
    private final UserRepository userRepository;

    private String currentLoginId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private Long currentTeamIdOrThrow() {
        var user = userRepository.findByLoginId(currentLoginId())
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));
        if (user.getCurrentTeamId() == null) throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        return user.getCurrentTeamId();
    }

    public boolean isUnlocked(Long challengeId) {
        Long teamId = currentTeamIdOrThrow();
        return unlockRepo.existsByTeamIdAndChallengeId(teamId, challengeId);
    }

    public SignatureDto.StatusResponse status(Long challengeId) {
        Long teamId = currentTeamIdOrThrow();
        boolean unlocked = unlockRepo.existsByTeamIdAndChallengeId(teamId, challengeId);
        return SignatureDto.StatusResponse.builder()
                .unlocked(unlocked).teamId(teamId).challengeId(challengeId).build();
    }

    @Transactional
    public SignatureDto.CheckResponse checkAndUnlock(Long challengeId, SignatureDto.Request req) {
        Long teamId = currentTeamIdOrThrow();

        ChallengeSignaturePolicy policy = policyRepo.findByChallengeId(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.REQUIRED_FIELD_NULL)); // 정책 누락

        boolean valid = policy.getName().equals(req.getName())
                && policy.getClub().equals(req.getClub())
                && policy.getSignature().equals(req.getSignature());

        if (!valid) {
            return SignatureDto.CheckResponse.builder()
                    .valid(false)
                    .unlocked(unlockRepo.existsByTeamIdAndChallengeId(teamId, challengeId))
                    .teamId(teamId).challengeId(challengeId).build();
        }

        if (!unlockRepo.existsByTeamIdAndChallengeId(teamId, challengeId)) {
            unlockRepo.save(TeamSignatureUnlockEntity.builder()
                    .teamId(teamId)
                    .challengeId(challengeId)
                    .signature(req.getSignature())
                    .name(req.getName())
                    .club(req.getClub())
                    .unlockedAt(LocalDateTime.now())
                    .build());
            log.info("Signature unlocked: team={} challenge={}", teamId, challengeId);
        }

        return SignatureDto.CheckResponse.builder()
                .valid(true).unlocked(true).teamId(teamId).challengeId(challengeId).build();
    }
}
