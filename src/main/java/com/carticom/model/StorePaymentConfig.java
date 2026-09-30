package com.carticom.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "store_payment_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorePaymentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", unique = true, nullable = false)
    private Store store;

    private String paystackSecretKey;

    private String paystackPublicKey;

    private String flutterwaveSecretKey;

    private String flutterwaveVerifyHash;

    private String activeProvider;

    private String virtualAccountNumber;

    private String virtualAccountName;

    private String virtualBankName;

    private String virtualAccountProvider;

    private LocalDateTime connectedAt;
}
