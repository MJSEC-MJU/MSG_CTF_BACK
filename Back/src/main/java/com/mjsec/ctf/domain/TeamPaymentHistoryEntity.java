package com.mjsec.ctf.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamPaymentHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long teamPaymentHistoryId;

    @Column(nullable = false)
    private Long teamId;

    @Column(nullable = false)
    private Long requesterUserId;

    @Column(nullable = false)
    private int mileageUsed;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
