package com.arknow.profile.api.dto;

import java.time.LocalDate;

public record ProfilePatchRequest(
        String nickname,
        String bio,
        String gender,
        LocalDate birthday,
        String zgId,
        String school,
        String tagJson
) {
}
