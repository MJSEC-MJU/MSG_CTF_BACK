package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.ContestConfigEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContestConfigRepository extends JpaRepository<ContestConfigEntity, Long> {

    Optional<ContestConfigEntity> findFirstByIsActiveTrueOrderByIdDesc();
}
