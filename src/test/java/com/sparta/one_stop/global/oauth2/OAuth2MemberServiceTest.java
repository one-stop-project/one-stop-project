package com.sparta.one_stop.global.oauth2;

import com.sparta.one_stop.domain.admin.security.SuspensionPolicyService;
import com.sparta.one_stop.domain.user.entity.User;
import com.sparta.one_stop.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OAuth2MemberServiceTest {

    @Test
    void existing_user_applies_suspension_expiry_policy_before_active_check() {
        UserRepository users = mock(UserRepository.class);
        OAuth2NewUserCreator creator = mock(OAuth2NewUserCreator.class);
        SuspensionPolicyService suspensionPolicy = mock(SuspensionPolicyService.class);
        OAuth2UserInfo userInfo = mock(OAuth2UserInfo.class);
        User user = mock(User.class);
        when(userInfo.getProvider()).thenReturn("kakao");
        when(userInfo.getProviderId()).thenReturn("provider-id");
        when(users.findByProviderAndProviderId("kakao", "provider-id"))
            .thenReturn(Optional.of(user));

        User result = new OAuth2MemberService(users, creator, suspensionPolicy)
            .findOrCreateUser(userInfo);

        assertThat(result).isSameAs(user);
        InOrder order = inOrder(suspensionPolicy, user);
        order.verify(suspensionPolicy).validateOrRelease(user);
        order.verify(user).verifyActive();
        order.verify(user).recordLogin();
    }
}
