package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    boolean existsByLoginId(String loginId);
    boolean existsByEmail(String email);

    Optional<UserEntity> findByLoginId(String loginId);// 로그인 ID로 유저 조회
    Optional<UserEntity> findByEmail(String email);
}