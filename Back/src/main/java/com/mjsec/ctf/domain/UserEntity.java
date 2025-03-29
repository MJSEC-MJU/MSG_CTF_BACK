package com.mjsec.ctf.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String univ;

    /* 처음엔 UserRole로 설정했으나 ERD 설계로 String 타입으로 변경
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @Fetch(FetchMode.JOIN)
    private List<UserRole> roles;
     */

    @Column(nullable = false)
    private String roles;

    @Column(nullable = false)
    private int totalPoint;
}