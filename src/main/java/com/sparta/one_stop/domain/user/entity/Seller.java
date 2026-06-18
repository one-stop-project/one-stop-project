package com.sparta.one_stop.domain.user.entity;

import com.sparta.one_stop.global.enums.user.SellerStatus;
import com.sparta.one_stop.global.exception.CustomException;
import com.sparta.one_stop.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seller")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seller_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "shop_name", nullable = false, length = 100)
    private String shopName;

    @Column(name = "business_number", nullable = false, length = 20)
    private String businessNumber;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "bank_account", length = 50)
    private String bankAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SellerStatus status;

    @Builder
    private Seller(User user, String shopName, String businessNumber, String bankName, String bankAccount) {
        this.user = user;
        this.shopName = shopName;
        this.businessNumber = businessNumber;
        this.bankName = bankName;
        this.bankAccount = bankAccount;
        this.status = SellerStatus.PENDING;
    }

    public void approve() {
        this.status = SellerStatus.APPROVED;
    }

    public void reject() {
        this.status = SellerStatus.REJECTED;
    }

    public boolean isPending() {
        return this.status == SellerStatus.PENDING;
    }

    public boolean isApproved() {
        return this.status == SellerStatus.APPROVED;
    }

    public void suspend() {
        if (this.status != SellerStatus.APPROVED) {
            throw new CustomException(ErrorCode.ADMIN_004);
        }
        this.status = SellerStatus.SUSPENDED;
    }

    public void reactivate() {
        if (this.status != SellerStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.ADMIN_004);
        }
        this.status = SellerStatus.APPROVED;
    }
}

