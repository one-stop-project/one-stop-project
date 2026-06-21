package com.sparta.one_stop.domain.admin.security;
import jakarta.validation.constraints.*;
public record SecurityActionRequest(@NotNull SecurityActionType actionType,@NotBlank @Size(max=100) String reasonCode,
 @Size(max=1000) String reasonDetail,@Min(1) @Max(43200) Integer durationMinutes){}
