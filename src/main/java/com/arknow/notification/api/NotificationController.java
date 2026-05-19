package com.arknow.notification.api;

import com.arknow.notification.mapper.NotificationMapper;
import com.arknow.notification.model.Notification;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationMapper mapper;

    public NotificationController(NotificationMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Map<String, Object> list(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(defaultValue = "0") int offset,
                                     @RequestParam(defaultValue = "20") int limit) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        List<Notification> items = mapper.listByUser(uid, offset, limit);
        int unread = mapper.countUnread(uid);
        return Map.of("items", items, "unread", unread);
    }

    @GetMapping("/unread-count")
    public Map<String, Integer> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        return Map.of("count", mapper.countUnread(uid));
    }

    @PostMapping("/read-all")
    public Map<String, Boolean> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        mapper.markRead(uid);
        return Map.of("success", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Boolean> delete(@PathVariable long id,
                                       @AuthenticationPrincipal Jwt jwt) {
        long uid = Long.parseLong(jwt.getClaimAsString("uid"));
        int rows = mapper.deleteById(id, uid);
        if (rows == 0) {
            throw new BusinessException(
                ErrorCode.BAD_REQUEST, "通知不存在或无权删除");
        }
        return Map.of("success", true);
    }
}
