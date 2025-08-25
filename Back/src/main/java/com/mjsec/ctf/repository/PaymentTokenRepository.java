package com.mjsec.ctf.repository;

import com.mjsec.ctf.domain.PaymentTokenEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentTokenRepository extends JpaRepository<PaymentTokenEntity, Long> {

    Optional<PaymentTokenEntity> findByPaymentToken(String paymentToken);

    void deleteByPaymentToken(String paymentToken);
}
