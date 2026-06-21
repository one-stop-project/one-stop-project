package com.sparta.one_stop.domain.admin.security;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface UserSecurityActionRepository extends JpaRepository<UserSecurityAction,Long>{
 @Query("select a from UserSecurityAction a where a.targetUserId=:userId and a.actionType=com.sparta.one_stop.domain.admin.security.SecurityActionType.SUSPEND and a.active=true order by a.startedAt desc limit 1")
 Optional<UserSecurityAction> findActiveSuspendAction(@Param("userId") Long userId);
}
