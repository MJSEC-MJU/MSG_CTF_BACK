package com.mjsec.ctf.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class RefreshEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String loginId;

    private String refresh;

    private String expiration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loginId", referencedColumnName = "loginId", insertable = false, updatable = false)
    private UserEntity user;

}
