package com.sparta.one_stop.domain.admin.repository;

import com.sparta.one_stop.domain.admin.entity.AdminActionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminActionHistoryRepository extends JpaRepository<AdminActionHistory, Long> {
}
