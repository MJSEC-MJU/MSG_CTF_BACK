package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.TeamPaymentHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamPaymentHistoryRepository extends JpaRepository<TeamPaymentHistoryEntity, Long> {
}
