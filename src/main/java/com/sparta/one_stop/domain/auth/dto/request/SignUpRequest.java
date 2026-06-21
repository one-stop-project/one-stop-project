package com.sparta.one_stop.domain.auth.dto.request;

import com.sparta.one_stop.global.enums.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
        message = "비밀번호는 8자 이상, 영문+숫자+특수문자를 포함해야 합니다"
    )
    String password,

    @NotBlank(message = "이름은 필수입니다")
    @Size(min = 2, max = 20, message = "이름은 2~20자여야 합니다")
    String name,

    @NotBlank(message = "전화번호는 필수입니다")
    @Pattern(
        regexp = "^010-\\d{4}-\\d{4}$",
        message = "전화번호는 010-0000-0000 형식이어야 합니다"
    )
    String phone,

    String address,

    String detailAddress,

    @NotNull(message = "역할은 필수입니다")
    UserRole role,

    String shopName,
    String businessNumber,
    String bankName,
    String bankAccount
) {
}
