package com.carticom.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "store_members", uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Store-scoped display role (ADMIN / MANAGER / STAFF / VIEWER).
     * Kept separate from the user's global Role so demoting/promoting a
     * team member here never breaks their ability to access the store.
     */
    private String displayRole;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
