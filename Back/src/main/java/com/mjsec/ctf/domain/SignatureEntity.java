package com.mjsec.ctf.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Table(name = "signature_entity")
@SQLDelete(sql = "UPDATE signature SET deleted_at = NOW() WHERE signature_id = ?")
@SQLRestriction("deleted_at is null")
public class SignatureEntity extends BaseEntity {

    @Id
    @Column(nullable = false, unique = true)
    private String signature;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String club;
}
