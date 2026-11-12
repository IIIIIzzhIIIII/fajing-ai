package com.fajing.service;

import com.fajing.entity.AdminLog;
import com.fajing.mapper.AdminLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 管理端写操作日志：从 AdminInterceptor 写入的 adminId 取当前管理员
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLogService {

    private final AdminLogMapper adminLogMapper;

    public void log(String action, String target) {
        try {
            AdminLog logRow = new AdminLog();
            logRow.setAdminId(currentAdminId());
            logRow.setAction(action);
            logRow.setTarget(target == null ? "" : target);
            logRow.setCreatedAt(LocalDateTime.now());
            adminLogMapper.insert(logRow);
        } catch (Exception e) {
            log.warn("写管理端操作日志失败：{}", e.getMessage());
        }
    }

    private Long currentAdminId() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return 0L;
        }
        HttpServletRequest request = attrs.getRequest();
        Object id = request.getAttribute("adminId");
        if (id instanceof Long value) {
            return value;
        }
        return 0L;
    }
}
