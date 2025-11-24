package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.RefreshEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshRepository extends JpaRepository<RefreshEntity,Long> {

    Boolean existsByRefresh(String refresh);

    @Transactional
    void  deleteByRefresh(String refresh);

    @Transactional
    void deleteByLoginId(String loginId);
}
