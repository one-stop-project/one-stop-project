package com.sparta.one_stop.domain.point.repository;

import com.sparta.one_stop.domain.point.entity.Point;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointRepository extends JpaRepository<Point, Long> {

    // 사용자 ID 기준 포인트 계정 조회
    // 포인트 잔액 조회, 충전, 차감, 복구 시 사용한다
    Optional<Point> findByUserId(Long userId);

    // 사용자 ID 기준 포인트 계정 존재 여부 확인
    // 회원가입 시 포인트 계정 중복 생성 방지 등에 사용한다
    boolean existsByUserId(Long userId);

}
