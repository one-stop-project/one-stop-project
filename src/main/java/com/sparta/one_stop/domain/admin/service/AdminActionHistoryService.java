package com.sparta.one_stop.domain.admin.service;

import com.sparta.one_stop.domain.admin.dto.AdminActionHistoryResponse;
import com.sparta.one_stop.domain.admin.repository.AdminActionHistoryRepository;
import com.sparta.one_stop.global.enums.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminActionHistoryService {

    private final AdminActionHistoryRepository adminActionHistoryRepository;

    public Page<AdminActionHistoryResponse> getHistories(Long actorId, UserRole role, Pageable pageable) {
        if (role == UserRole.SUPER_ADMIN) {
            return adminActionHistoryRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(AdminActionHistoryResponse::from);
        }
        return adminActionHistoryRepository.findAllByActorIdOrderByCreatedAtDesc(actorId, pageable)
            .map(AdminActionHistoryResponse::from);
    }
}
