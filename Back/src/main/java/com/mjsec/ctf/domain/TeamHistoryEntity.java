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
public class TeamHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyid;

    private String teamName;

    private Long challengeId;

    private LocalDateTime solvedTime;


    @Column(nullable = false)
    @Builder.Default
    private boolean userDeleted = false;

}
