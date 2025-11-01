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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
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

    public TeamService(TeamRepository teamRepository, UserRepository userRepository,
                       TeamPaymentHistoryRepository teamPaymentHistoryRepository,
                       TeamHistoryRepository teamHistoryRepository,
                       ChallengeRepository challengeRepository,
                       HistoryRepository historyRepository) {

        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.teamPaymentHistoryRepository = teamPaymentHistoryRepository;
        this.teamHistoryRepository = teamHistoryRepository;
        this.challengeRepository = challengeRepository;
        this.historyRepository = historyRepository;
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

        // 🔴 전달받은 points를 직접 추가 (recalculate 대신 증분 업데이트)
        if (points > 0) {
            team.setTotalPoint(team.getTotalPoint() + points);
        }

        // lastSolvedTime 업데이트 (addSolvedChallenge에서 이미 설정되지만 명시적으로)
        team.setLastSolvedTime(java.time.LocalDateTime.now());

        teamRepository.save(team);

        log.info("[팀 점수 증분 업데이트] teamId={}, challengeId={}, addedPoints={}, newTotal={}",
                team.getTeamId(), challengeId, points, team.getTotalPoint());
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
