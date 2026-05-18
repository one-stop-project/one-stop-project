package com.sparta.one_stop.domain.user.repository;

import com.sparta.one_stop.domain.user.entity.Seller;
import com.sparta.one_stop.global.enums.user.SellerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SellerRepository extends JpaRepository<Seller, Long> {

    Optional<Seller> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    // 상태별 판매자 목록 조회 (관리자 승인/반려 목록용)
    List<Seller> findAllByStatus(SellerStatus status);

}
