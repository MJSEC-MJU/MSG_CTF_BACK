package com.mjsec.ctf.service;

import com.mjsec.ctf.domain.ChallengeEntity;
import com.mjsec.ctf.domain.HistoryEntity;
import com.mjsec.ctf.domain.TeamEntity;
import com.mjsec.ctf.domain.TeamHistoryEntity;
import com.mjsec.ctf.domain.TeamPaymentHistoryEntity;
import com.mjsec.ctf.domain.UserEntity;
import com.mjsec.ctf.dto.TeamHistoryDto;
import com.mjsec.ctf.dto.TeamProfileDto;
import com.mjsec.ctf.dto.TeamSummaryDto;
import com.mjsec.ctf.exception.RestApiException;
import com.mjsec.ctf.repository.ChallengeRepository;
import com.mjsec.ctf.repository.HistoryRepository;
import com.mjsec.ctf.repository.TeamHistoryRepository;
import com.mjsec.ctf.repository.TeamPaymentHistoryRepository;
import com.mjsec.ctf.repository.TeamRepository;
import com.mjsec.ctf.repository.UserRepository;
import com.mjsec.ctf.type.ChallengeCategory;
import com.mjsec.ctf.type.ErrorCode;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TeamPaymentHistoryRepository teamPaymentHistoryRepository;
    private final TeamHistoryRepository teamHistoryRepository;
    private final ChallengeRepository challengeRepository;
    private final HistoryRepository historyRepository;
    private final ChallengeService challengeService;

    public TeamService(TeamRepository teamRepository, UserRepository userRepository,
                       TeamPaymentHistoryRepository teamPaymentHistoryRepository,
                       TeamHistoryRepository teamHistoryRepository,
                       ChallengeRepository challengeRepository,
                       HistoryRepository historyRepository,
                       @Lazy ChallengeService challengeService) {

        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.teamPaymentHistoryRepository = teamPaymentHistoryRepository;
        this.teamHistoryRepository = teamHistoryRepository;
        this.challengeRepository = challengeRepository;
        this.historyRepository = historyRepository;
        this.challengeService = challengeService;
    }

    public void createTeam(String teamName) {

        if (teamRepository.existsByTeamName(teamName)) {
            throw new RestApiException(ErrorCode.TEAM_ALREADY_EXIST);
        }

        TeamEntity team = TeamEntity.builder()
                .teamName(teamName)
                .mileage(0)
                .totalPoint(0)
                .build();

        teamRepository.save(team);
    }

    public void saveTeam(TeamEntity team) {
        teamRepository.save(team);
    }   //메서드 추가 (10/6)


    @Transactional
    public void addMember(String teamName, String email) {

        TeamEntity team = teamRepository.findByTeamName(teamName)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (user.hasTeam()) {
            throw new RestApiException(ErrorCode.ALREADY_HAVE_TEAM);
        }

        if(!team.getMemberUserIds().isEmpty() && team.getMemberUserIds().size() == 2) {
            throw new RestApiException(ErrorCode.TEAM_FULL);
        }

        team.addMember(user.getUserId());
        teamRepository.save(team);

        user.joinTeam(team.getTeamId());
        userRepository.save(user);
    }

    @Transactional
    public void deleteMember(String teamName, String email) {

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (!user.hasTeam()) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        TeamEntity team = teamRepository.findByTeamName(teamName)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        if(!team.isMember(user.getUserId())) {
            throw new RestApiException(ErrorCode.TEAM_MISMATCH);
        }

        team.removeMember(user.getUserId());
        user.leaveTeam();

        teamRepository.save(team);
        userRepository.save(user);
    }

    public boolean canSolveChallenge(Long userId, Long challengeId) {

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (!user.hasTeam()) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        TeamEntity team = teamRepository.findById(user.getCurrentTeamId())
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        return !team.hasSolvedChallenge(challengeId);
    }

    @Transactional
    public void recordTeamSolution(Long userId, Long challengeId, int points, int mileage) {

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (!user.hasTeam()) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        TeamEntity team = teamRepository.findById(user.getCurrentTeamId())
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        boolean newlySolved = team.addSolvedChallenge(challengeId);
        if (!newlySolved) {
            log.debug("Team {} already solved challenge {}, skipping duplicate submission.", team.getTeamId(), challengeId);
            return;
        }

        if (mileage > 0) {
            team.addMileage(mileage);
        }

        recalculateSingleTeam(team);
    }

    public boolean useTeamMileage(Long teamId, int amount, Long requesterUserId) {

        TeamEntity team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        if (team.deductMileage(amount)) {
            teamRepository.save(team);

            TeamPaymentHistoryEntity paymentHistory = TeamPaymentHistoryEntity.builder()
                    .teamId(teamId)
                    .requesterUserId(requesterUserId)
                    .mileageUsed(amount)
                    .createdAt(LocalDateTime.now())
                    .build();

            teamPaymentHistoryRepository.save(paymentHistory);
            return true;
        }
        return false;
    }

    public Optional<TeamEntity> getUserTeam(Long teamId) {

        return teamRepository.findById(teamId);
    }

    @Transactional
    public void recalculateTeamPoints(Long teamId) {
        TeamEntity team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));
        recalculateSingleTeam(team);
    }

    @Transactional
    public void recalculateTeamPoints(TeamEntity team) {
        if (team == null || team.getTeamId() == null) {
            throw new RestApiException(ErrorCode.TEAM_NOT_FOUND);
        }
        recalculateSingleTeam(team);
    }

    @Transactional
    public void recalculateTeamsByChallenge(Long challengeId) {
        List<TeamEntity> teams = teamRepository.findTeamsBySolvedChallengeId(String.valueOf(challengeId));
        for (TeamEntity team : teams) {
            recalculateSingleTeam(team);
        }
    }

    @Transactional
    public void recalculateAllTeamPoints() {
        List<TeamEntity> teams = teamRepository.findAll();
        for (TeamEntity team : teams) {
            recalculateSingleTeam(team);
        }
    }

    public List<TeamEntity> getTeamRanking() {

        return teamRepository.findAllByOrderByTotalPointDescLastSolvedTimeAsc();
    }

    public TeamProfileDto getTeamProfile(String loginId) {

        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (user.getCurrentTeamId() == null) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        TeamEntity team = getUserTeam(user.getCurrentTeamId())
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        List<String> memberEmails = new ArrayList<>();
        if (!team.getMemberUserIds().isEmpty()) {
            memberEmails = userRepository.findAllById(team.getMemberUserIds()).stream()
                    .map(UserEntity::getEmail)
                    .collect(Collectors.toList());
        }

        return TeamProfileDto.builder()
                .teamId(team.getTeamId())
                .teamName(team.getTeamName())
                .userEmail(user.getEmail())
                .memberEmail(memberEmails)
                .teamMileage(team.getMileage())
                .teamTotalPoint(team.getTotalPoint())
                .teamSolvedCount(team.getSolvedCount())
                .build();
    }

    public List<Long> getTeamSolvedChallenges(Long teamId) {

        TeamEntity team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        return team.getSolvedChallengeIds();
    }

    public List<String> getTeamMemberLoginIds(List<Long> memberUserIds) {
        if (memberUserIds == null || memberUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        return userRepository.findAllById(memberUserIds).stream()
                .map(UserEntity::getLoginId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<TeamSummaryDto> getAllTeams() {

        List<TeamEntity> teams = teamRepository.findAll();

        return teams.stream()
                .map(team -> {
                    List<String> memberEmails = new ArrayList<>();
                    if (team.getMemberUserIds() != null && !team.getMemberUserIds().isEmpty()) {
                        memberEmails = userRepository.findAllById(team.getMemberUserIds()).stream()
                                .map(UserEntity::getEmail)
                                .collect(Collectors.toList());
                    }

                    return TeamSummaryDto.builder()
                            .teamId(team.getTeamId())
                            .teamName(team.getTeamName())
                            .teamTotalPoint(team.getTotalPoint())
                            .teamMileage(team.getMileage())
                            .memberEmails(memberEmails)
                            .build();
                })
                .collect(Collectors.toList());
    }

    // 관리자가 팀에 마일리지 부여
    @Transactional
    public void grantMileageToTeam(String teamName, int mileage) {
        TeamEntity team = teamRepository.findByTeamName(teamName)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        if (mileage < 0) {
            throw new RestApiException(ErrorCode.BAD_REQUEST, "마일리지는 0 이상이어야 합니다.");
        }

        team.addMileage(mileage);
        teamRepository.save(team);

        log.info("Admin granted mileage: teamId={}, teamName={}, mileageGranted={}",
                team.getTeamId(), teamName, mileage);
    }

    // 팀 히스토리 조회 (자기 팀의 풀이 기록)
    public List<TeamHistoryDto> getTeamHistory(String loginId) {
        log.info("팀 히스토리 조회 시작: loginId={}", loginId);

        UserEntity user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new RestApiException(ErrorCode.USER_NOT_FOUND));

        if (user.getCurrentTeamId() == null) {
            throw new RestApiException(ErrorCode.MUST_BE_BELONG_TEAM);
        }

        TeamEntity team = teamRepository.findById(user.getCurrentTeamId())
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        // 팀원 loginId 목록
        List<String> memberLoginIds = getTeamMemberLoginIds(team.getMemberUserIds());

        List<TeamHistoryEntity> histories = teamHistoryRepository.findByTeamNameOrderBySolvedTimeAsc(team.getTeamName());

        List<TeamHistoryDto> historyDtos = histories.stream()
                .map(history -> {
                    Optional<ChallengeEntity> challengeOpt = challengeRepository.findById(history.getChallengeId());
                    if (!challengeOpt.isPresent()) {
                        return null;
                    }

                    ChallengeEntity challenge = challengeOpt.get();

                    // 해당 문제를 누가 풀었는지 찾기 (팀원 중에서)
                    String solvedBy = null;
                    for (String memberLoginId : memberLoginIds) {
                        Optional<HistoryEntity> individualHistory =
                                historyRepository.findByLoginIdAndChallengeId(memberLoginId, history.getChallengeId());
                        if (individualHistory.isPresent()) {
                            solvedBy = memberLoginId;
                            break;
                        }
                    }

                    return new TeamHistoryDto(
                            team.getTeamId(),
                            team.getTeamName(),
                            String.valueOf(challenge.getChallengeId()),
                            challenge.getTitle(),
                            history.getSolvedTime(),
                            challenge.getPoints(),
                            solvedBy  // 풀이한 팀원
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("팀 히스토리 조회 완료: loginId={}, teamName={}, recordCount={}",
                loginId, team.getTeamName(), historyDtos.size());

        return historyDtos;
    }


    /**
     * 팀 삭제 메서드
     * - 팀 제출 히스토리 삭제 및 영향받은 문제들의 solvers 감소
     * - 영향받은 문제들의 다이나믹 스코어 재계산 (ChallengeService.updateChallengeScore 활용)
     * - 팀 결제 히스토리 삭제
     * - 팀원들의 팀 소속 해제 (유저는 삭제하지 않음)
     * - 전체 팀 점수 재계산
     * - 팀 삭제
     */
    @Transactional
    public void deleteTeam(String teamName) {
        log.info("팀 삭제 시작: teamName={}", teamName);

        long startTime = System.currentTimeMillis();

        // 팀 존재 확인
        TeamEntity team = teamRepository.findByTeamName(teamName)
                .orElseThrow(() -> new RestApiException(ErrorCode.TEAM_NOT_FOUND));

        Long teamId = team.getTeamId();
        List<Long> memberUserIds = team.getMemberUserIds();

        // 팀원들의 loginId 조회
        List<String> memberLoginIds = new ArrayList<>();
        if (memberUserIds != null && !memberUserIds.isEmpty()) {
            for (Long userId : memberUserIds) {
                Optional<UserEntity> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    memberLoginIds.add(userOpt.get().getLoginId());
                }
            }
        }

        // 팀 제출 히스토리 삭제 및 영향받은 문제 ID 수집
        List<TeamHistoryEntity> teamHistories = teamHistoryRepository.findByTeamNameOrderBySolvedTimeAsc(teamName);
        Set<Long> affectedChallengeIds = new HashSet<>();

        if (!teamHistories.isEmpty()) {
            // 영향받은 문제 ID 수집
            for (TeamHistoryEntity history : teamHistories) {
                affectedChallengeIds.add(history.getChallengeId());
            }

            // 팀 제출 히스토리 삭제
            teamHistoryRepository.deleteAll(teamHistories);
            log.info("팀 제출 히스토리 삭제 완료: teamName={}, 삭제된 히스토리 개수={}", teamName, teamHistories.size());
        }

        // 영향받은 문제들의 퍼스트 블러드 확인
        Map<Long, Boolean> wasFirstBloodMap = new HashMap<>();
        for (Long challengeId : affectedChallengeIds) {
            // 해당 문제의 모든 히스토리 조회
            List<HistoryEntity> allHistories = historyRepository.findByChallengeId(challengeId);

            if (!allHistories.isEmpty()) {
                // 전체 중 가장 빠른 제출 찾기
                Optional<HistoryEntity> globalFirstBloodOpt = allHistories.stream()
                        .filter(h -> h.getLoginId() != null)
                        .min(Comparator.comparing(HistoryEntity::getSolvedTime));

                // 삭제된 팀원이 퍼스트 블러드였는지 확인
                boolean wasFirstBlood = false;
                if (globalFirstBloodOpt.isPresent()) {
                    String firstBloodLoginId = globalFirstBloodOpt.get().getLoginId();
                    wasFirstBlood = memberLoginIds.contains(firstBloodLoginId);
                }

                wasFirstBloodMap.put(challengeId, wasFirstBlood);
            }
        }

        // 삭제된 팀원의 개인 히스토리 삭제 (영향받은 문제만)
        for (Long challengeId : affectedChallengeIds) {
            for (String loginId : memberLoginIds) {
                Optional<HistoryEntity> historyOpt = historyRepository.findByLoginIdAndChallengeId(loginId, challengeId);
                if (historyOpt.isPresent()) {
                    historyRepository.delete(historyOpt.get());
                    log.info("개인 히스토리 삭제: loginId={}, challengeId={}", loginId, challengeId);
                }
            }
        }

        // 영향받은 문제들의 solvers 감소, 다이나믹 스코어 재계산, 퍼스트 블러드 재할당
        for (Long challengeId : affectedChallengeIds) {
            Optional<ChallengeEntity> challengeOpt = challengeRepository.findById(challengeId);
            if (challengeOpt.isPresent()) {
                ChallengeEntity challenge = challengeOpt.get();

                // solvers 감소
                challenge.setSolvers(Math.max(0, challenge.getSolvers() - 1));
                challengeRepository.save(challenge);
                log.info("Challenge solvers 감소: challengeId={}, newSolvers={}", challengeId, challenge.getSolvers());

                // 다이나믹 스코어 재계산 (SIGNATURE 카테고리 제외)
                if (challenge.getCategory() != ChallengeCategory.SIGNATURE) {
                    challengeService.updateChallengeScore(challenge);
                    log.info("다이나믹 스코어 재계산 완료: challengeId={}, newPoints={}",
                            challengeId, challenge.getPoints());
                }

                // 퍼스트 블러드 재할당
                Boolean wasFirstBlood = wasFirstBloodMap.get(challengeId);
                if (Boolean.TRUE.equals(wasFirstBlood)) {
                    reassignFirstBlood(challengeId);
                }
            }
        }

        // 팀 결제 히스토리 삭제
        List<TeamPaymentHistoryEntity> paymentHistories = teamPaymentHistoryRepository.findByTeamIdOrderByCreatedAtDesc(teamId);
        if (!paymentHistories.isEmpty()) {
            teamPaymentHistoryRepository.deleteAll(paymentHistories);
            log.info("팀 결제 히스토리 삭제 완료: teamName={}, 삭제된 결제 히스토리 개수={}", teamName, paymentHistories.size());
        }

        // 팀원들의 팀 소속 해제 (유저는 삭제하지 않음)
        if (memberUserIds != null && !memberUserIds.isEmpty()) {
            for (Long userId : memberUserIds) {
                Optional<UserEntity> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    UserEntity user = userOpt.get();
                    user.leaveTeam(); // currentTeamId를 null로 설정
                    userRepository.save(user);
                    log.info("팀원 소속 해제: userId={}, loginId={}", userId, user.getLoginId());
                }
            }
            log.info("팀원 소속 해제 완료: teamName={}, 해제된 팀원 수={}", teamName, memberUserIds.size());
        }

        // 전체 팀 점수 재계산 (다이나믹 스코어 변경으로 인한 다른 팀들의 점수 재계산)
        recalculateAllTeamPoints();
        log.info("전체 팀 점수 재계산 완료");

        // 팀 삭제
        teamRepository.delete(team);

        long duration = System.currentTimeMillis() - startTime;
        log.info("팀 삭제 완료: teamName={}, teamId={}, 영향받은 문제 수={}, 소요시간={}ms",
                teamName, teamId, affectedChallengeIds.size(), duration);
    }

    @Transactional
    public void reassignFirstBlood(Long challengeId) {
        log.info("퍼스트 블러드 재할당 시작: challengeId={}", challengeId);

        ChallengeEntity challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new RestApiException(ErrorCode.CHALLENGE_NOT_FOUND));

        // 삭제 후 남은 제출 기록 중 가장 빠른 것 찾기
        List<HistoryEntity> remainingHistories = historyRepository.findByChallengeId(challengeId);

        Optional<HistoryEntity> newFirstBloodOpt = remainingHistories.stream()
                .filter(h -> h.getLoginId() != null)
                .min(Comparator.comparing(HistoryEntity::getSolvedTime));

        if (newFirstBloodOpt.isPresent()) {
            HistoryEntity newFirstBloodHistory = newFirstBloodOpt.get();

            Optional<UserEntity> newFirstUserOpt = userRepository.findByLoginId(newFirstBloodHistory.getLoginId());

            if (newFirstUserOpt.isPresent()) {
                UserEntity newFirstUser = newFirstUserOpt.get();

                if (newFirstUser.getCurrentTeamId() != null) {
                    Optional<TeamEntity> newFirstTeamOpt = teamRepository.findById(newFirstUser.getCurrentTeamId());

                    if (newFirstTeamOpt.isPresent()) {
                        TeamEntity newFirstTeam = newFirstTeamOpt.get();

                        // 보너스 마일리지 계산 (30%)
                        int baseMileage = challenge.getMileage();
                        int bonus = (int) Math.ceil(baseMileage * 0.30);

                        // 새 퍼스트 블러드 팀에게 보너스만 추가 지급
                        // (기본 마일리지는 이미 받았으므로 보너스만 추가)
                        newFirstTeam.addMileage(bonus);
                        teamRepository.save(newFirstTeam);

                        log.info("새 퍼스트 블러드 보너스 지급: teamId={}, teamName={}, bonus={}, challengeId={}, loginId={}",
                                newFirstTeam.getTeamId(), newFirstTeam.getTeamName(), bonus, challengeId, newFirstBloodHistory.getLoginId());
                    } else {
                        log.warn("새 퍼스트 블러드 팀을 찾을 수 없음: teamId={}", newFirstUser.getCurrentTeamId());
                    }
                } else {
                    log.warn("새 퍼스트 블러드 유저에게 팀이 없음: loginId={}", newFirstUser.getLoginId());
                }
            } else {
                log.warn("새 퍼스트 블러드 유저를 찾을 수 없음: loginId={}", newFirstBloodHistory.getLoginId());
            }
        } else {
            log.info("삭제 후 남은 제출 기록이 없음: challengeId={}", challengeId);
        }
    }

    private void recalculateSingleTeam(TeamEntity team) {
        List<Long> solvedChallengeIds = team.getSolvedChallengeIds();

        if (solvedChallengeIds == null || solvedChallengeIds.isEmpty()) {
            team.setTotalPoint(0);
            team.setLastSolvedTime(null);
            teamRepository.save(team);
            return;
        }

        List<ChallengeEntity> challenges = challengeRepository.findAllById(solvedChallengeIds);
        Map<Long, ChallengeEntity> challengeMap = challenges.stream()
                .collect(Collectors.toMap(ChallengeEntity::getChallengeId, Function.identity(), (left, right) -> left));

        List<HistoryEntity> histories = historyRepository.findByChallengeIdIn(solvedChallengeIds).stream()
                .filter(history -> !history.isUserDeleted())
                .collect(Collectors.toList());

        List<UserEntity> teamMembers = userRepository.findAllById(team.getMemberUserIds());
        Set<String> memberLoginIds = teamMembers.stream()
                .map(UserEntity::getLoginId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        int totalPoints = 0;
        LocalDateTime lastSolvedTime = null;

        for (Long challengeId : solvedChallengeIds) {
            ChallengeEntity challenge = challengeMap.get(challengeId);
            if (challenge == null) {
                continue;
            }

            if (challenge.getCategory() == ChallengeCategory.SIGNATURE) {
                continue;
            }

            totalPoints += challenge.getPoints();

            Optional<LocalDateTime> latestSolveTime = histories.stream()
                    .filter(history -> challengeId.equals(history.getChallengeId()))
                    .filter(history -> memberLoginIds.contains(history.getLoginId()))
                    .map(HistoryEntity::getSolvedTime)
                    .max(Comparator.naturalOrder());

            if (latestSolveTime.isPresent()) {
                LocalDateTime solvedTime = latestSolveTime.get();
                if (lastSolvedTime == null || solvedTime.isAfter(lastSolvedTime)) {
                    lastSolvedTime = solvedTime;
                }
            }
        }

        team.setTotalPoint(totalPoints);
        team.setLastSolvedTime(lastSolvedTime);
        teamRepository.save(team);
    }
}
