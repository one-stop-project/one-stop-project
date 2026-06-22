package com.sparta.one_stop.domain.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.RedisConnectionFailureException;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class RedisTokenServiceTest {
 @Test void refresh_rotation_returns_winning_token_during_grace(){
  RedisTemplate<String,String> redis=mock(RedisTemplate.class);
  given(redis.execute(any(RedisScript.class),anyList(),any(),any(),any(),any(),any()))
   .willReturn("GRACE:winning-token");

  RedisTokenService.RefreshTokenRotationResult result=new RedisTokenService(redis)
   .rotateRefreshToken(1L,"device-a","old-token","candidate-token",600L);

  assertThat(result.status())
   .isEqualTo(RedisTokenService.RefreshTokenRotationStatus.GRACE_REPLAY);
  assertThat(result.refreshToken()).isEqualTo("winning-token");
 }

 @Test void refresh_rotation_without_current_or_grace_is_reuse(){
  RedisTemplate<String,String> redis=mock(RedisTemplate.class);
  given(redis.execute(any(RedisScript.class),anyList(),any(),any(),any(),any(),any()))
   .willReturn("REUSED");

  RedisTokenService.RefreshTokenRotationResult result=new RedisTokenService(redis)
   .rotateRefreshToken(1L,"device-a","old-token","candidate-token",600L);

  assertThat(result.status())
   .isEqualTo(RedisTokenService.RefreshTokenRotationStatus.REUSED);
  assertThat(result.refreshToken()).isNull();
 }

 @Test void 전체로그아웃은_모든_refresh_key와_devices_key를_삭제한다(){
  RedisTemplate<String,String> redis=mock(RedisTemplate.class);
  given(redis.execute(any(RedisScript.class),eq(List.of("devices:1")),eq("RT:1:"))).willReturn(2L);
  long deleted=new RedisTokenService(redis).deleteAllRefreshTokensByUserId(1L);
  assertThat(deleted).isEqualTo(2);
  verify(redis).execute(any(RedisScript.class),eq(List.of("devices:1")),eq("RT:1:"));
 }

 @Test void oauth2_code_is_consumed_only_with_the_bound_device_id(){
  RedisTemplate<String,String> redis=mock(RedisTemplate.class);
  given(redis.execute(any(RedisScript.class),eq(List.of("oauth2:code:code-1")),eq("device-a")))
   .willReturn("device-a:access-token");
  RedisTokenService.OAuth2Handoff handoff=new RedisTokenService(redis)
   .consumeOAuth2Code("code-1","device-a");
  assertThat(handoff.deviceId()).isEqualTo("device-a");
 assertThat(handoff.accessToken()).isEqualTo("access-token");
 }

 @Test void security_logout_fails_closed_when_redis_is_unavailable(){
  RedisTemplate<String,String> redis=mock(RedisTemplate.class);
  given(redis.execute(any(RedisScript.class),anyList(),any()))
   .willThrow(new RedisConnectionFailureException("redis down"));
  assertThatThrownBy(()->new RedisTokenService(redis).deleteAllRefreshTokensByUserId(1L))
   .isInstanceOf(com.sparta.one_stop.global.exception.CustomException.class);
 }
}
