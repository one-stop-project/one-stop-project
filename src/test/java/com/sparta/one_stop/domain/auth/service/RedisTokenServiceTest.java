package com.sparta.one_stop.domain.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class RedisTokenServiceTest {
 @Test void 전체로그아웃은_모든_refresh_key와_devices_key를_삭제한다(){
  RedisTemplate<String,String> redis=mock(RedisTemplate.class);DeviceLimitService devices=mock(DeviceLimitService.class);
  ZSetOperations.TypedTuple<String> first=mock(ZSetOperations.TypedTuple.class);ZSetOperations.TypedTuple<String> second=mock(ZSetOperations.TypedTuple.class);
  given(first.getValue()).willReturn("device-a");given(second.getValue()).willReturn("device-b");
  given(devices.listDevices(1L)).willReturn(Set.of(first,second));
  given(redis.delete(argThat((java.util.Collection<String> keys)->keys.contains("RT:1:device-a")&&keys.contains("RT:1:device-b")))).willReturn(2L);
  long deleted=new RedisTokenService(redis,devices).deleteAllRefreshTokensByUserId(1L);
  assertThat(deleted).isEqualTo(2);verify(devices).removeAllDevices(1L);
 }
}
