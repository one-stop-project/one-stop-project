package com.sparta.one_stop.domain.review.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class UpdateReviewRequest {

    @NotNull(message = "별점은 필수입니다.")
    @Min(1)
    @Max(5)
    private Integer rating;

    @NotBlank(message = "리뷰 내용은 필수입니다.")
    @Size(min = 10, max = 1000)
    private String content;

    @NotNull
    @Size(max = 5)
    private List<String> imageUrls;
}
