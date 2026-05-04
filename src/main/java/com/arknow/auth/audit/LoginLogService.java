package com.arknow.auth.audit;

import org.springframework.stereotype.Service;

@Service
public class LoginLogService {
    private final LoginLogMapper mapper;

    public LoginLogService(LoginLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(Long userId, String identifier, String channel, String ip, String userAgent, String status) {
        LoginLog log = new LoginLog();
        log.setUserId(userId);
        log.setIdentifier(identifier);
        log.setChannel(channel);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setStatus(status);
        mapper.insert(log);
    }
}
