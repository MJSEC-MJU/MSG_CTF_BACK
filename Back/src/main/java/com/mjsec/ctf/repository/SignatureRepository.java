package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.SignatureEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SignatureRepository extends JpaRepository<SignatureEntity, String> {

    boolean existsByNameAndSignatureAndClub(String name, String signature, String club);

    Optional<SignatureEntity> findByNameAndSignatureAndClub(String name, String signature, String club);
}
