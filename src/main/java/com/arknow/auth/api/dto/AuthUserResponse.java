package com.arknow.auth.api.dto;

import java.time.LocalDate;

public record AuthUserResponse(
        Long id,
        String nickname,
        String avatar,
        String phone,
        String zgId,
        LocalDate birthday,
        String school,
        String bio,
        String gender,
        String tagJson
) {
}
