package com.sparta.one_stop.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentMethod {

    MOCK("MOCK"),
    CARD("CARD"),
    POINT("POINT");

    private final String description;

}
