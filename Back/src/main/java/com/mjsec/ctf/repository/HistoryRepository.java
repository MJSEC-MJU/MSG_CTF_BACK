package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.HistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HistoryRepository extends JpaRepository<HistoryEntity, Long> {
    // 필요 시 사용자별 조회 등의 메소드를 추가할 수 있음
    List<HistoryEntity> findAllByOrderBySolvedTimeAsc();
}
