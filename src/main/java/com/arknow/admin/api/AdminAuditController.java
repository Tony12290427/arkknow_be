package com.arknow.admin.api;

import com.arknow.auth.audit.LoginLog;
import com.arknow.auth.audit.LoginLogMapper;
import com.arknow.common.exception.BusinessException;
import com.arknow.common.exception.ErrorCode;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {
    private final LoginLogMapper loginLogMapper;

    public AdminAuditController(LoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        int offset = (page - 1) * size;
        List<LoginLog> items = loginLogMapper.listAll(offset, size);
        int total = loginLogMapper.countAll();
        return Map.of("items", items, "total", total, "page", page, "size", size);
    }

    @GetMapping("/{id}")
    public LoginLog detail(@PathVariable long id) {
        return loginLogMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "登录日志不存在"));
    }
}
