package com.mjsec.ctf.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class HistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String loginId;

    private Long challengeId;

    private LocalDateTime solvedTime;

    @Column(name = "univ", nullable=false)
    private String univ;

    @Column(nullable = false)
    @Builder.Default
    private boolean userDeleted = false;

    //사용자 삭제 처리
    public void anonymizeUser() {
        //loginId null처리
        this.loginId = null;
        //삭제 처리
        this.userDeleted = true;
    }

}
