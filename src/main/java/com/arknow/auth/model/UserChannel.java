package com.arknow.auth.model;

import lombok.Data;
import java.time.Instant;

@Data
public class UserChannel {
    private Long id;
    private Long userId;
    private String channelType;
    private String channelValue;
    private Instant verifiedAt;
    private Instant createdAt;
}
