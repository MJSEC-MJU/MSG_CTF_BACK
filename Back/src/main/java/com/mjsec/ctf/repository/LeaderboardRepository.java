package com.mjsec.ctf.repository;

import com.mjsec.ctf.entity.Leaderboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LeaderboardRepository extends JpaRepository<Leaderboard, Long> {

    // 모든 데이터를 total_point 기준 내림차순으로 가져오기
    // 데이터베이스의 값이 변경되었다는 것은 정답을 맞추거나 틀리거나 둘 중 하나의 상태인 것으로 생각됩니다.
    // 하여 정답을 맞춘 경우에도 업데이트 시간이 현재 시간으로 업데이트 되어 UpdatedAtAsc 를 통해
    // 데이터베이스 내 updated_at 의 값을 기준으로 다시 재정렬 되게 됩니다.
    // 테스트 시에도 후발주자가 더 낮은 순위를 갖는 것을 확인했습니다.
    List<Leaderboard> findAllByOrderByTotalPointDescUpdatedAtAsc();

}
