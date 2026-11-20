package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fajing.common.BizException;
import com.fajing.entity.AppUser;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.Staff;
import com.fajing.entity.Store;
import com.fajing.entity.Tenant;
import com.fajing.mapper.AppUserMapper;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.StaffMapper;
import com.fajing.mapper.StoreMapper;
import com.fajing.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 员工工作台：绑定、免费试发额度、员工码、顾客领取结果 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final StaffMapper staffMapper;
    private final StoreMapper storeMapper;
    private final TenantMapper tenantMapper;
    private final AppUserMapper userMapper;
    private final GenerationTaskMapper taskMapper;
    private final GenerationService generationService;
    private final GenerationProcessor processor;
    private final JdbcTemplate jdbc;

    public Map<String, Object> bind(Long userId, String inviteCode, String phone) {
        String code = inviteCode == null ? "" : inviteCode.trim().toUpperCase();
        String mobile = phone == null ? "" : phone.trim();
        if (code.isBlank()) {
            throw new BizException("请输入员工邀请码");
        }
        if (!mobile.matches("^1[3-9]\\d{9}$")) {
            throw new BizException("请输入正确的手机号");
        }
        Staff staff = staffMapper.selectOne(new QueryWrapper<Staff>()
                .eq("invite_code", code).eq("status", 1).last("limit 1"));
        if (staff == null) {
            throw new BizException("邀请码无效或员工已停用");
        }
        Store store = storeMapper.selectById(staff.getStoreId());
        if (store == null || store.getStatus() == null || store.getStatus() != 1) {
            throw new BizException("门店已停用，请联系店长");
        }
        Tenant tenant = tenantMapper.selectById(staff.getTenantId());
        if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) {
            throw new BizException("商户已停用，请联系平台管理员");
        }
        if (!mobile.equals(staff.getPhone())) {
            throw new BizException("手机号与店长录入的信息不一致");
        }
        AppUser user = userMapper.selectById(userId);
        if (user == null || user.getNickname() == null || user.getNickname().isBlank()
                || user.getPhone() == null || user.getPhone().isBlank()) {
            throw new BizException("请先在个人中心完成昵称与手机号授权");
        }
        if (!mobile.equals(user.getPhone())) {
            throw new BizException("请输入本人已授权的手机号");
        }
        if (staff.getUserId() != null && staff.getUserId() > 0 && !staff.getUserId().equals(userId)) {
            throw new BizException("该员工已绑定其他账号，请联系店长");
        }
        Long other = staffMapper.selectCount(new QueryWrapper<Staff>()
                .eq("user_id", userId).eq("status", 1).ne("id", staff.getId()));
        if (other != null && other > 0) {
            throw new BizException("当前账号已绑定其他员工身份");
        }
        staff.setUserId(userId);
        staffMapper.updateById(staff);
        return me(userId);
    }

    public Map<String, Object> me(Long userId) {
        Staff staff = activeStaff(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        if (staff == null) {
            data.put("bound", false);
            return data;
        }
        Store store = storeMapper.selectById(staff.getStoreId());
        Tenant tenant = store == null ? null : tenantMapper.selectById(store.getTenantId());
        int used = usedToday(staff.getId());
        int limit = staff.getDailyLimit() == null ? 20 : staff.getDailyLimit();

        data.put("bound", true);
        data.put("staffId", staff.getId());
        data.put("staffName", staff.getName());
        data.put("phone", staff.getPhone());
        data.put("staffCode", staff.getStaffCode());
        data.put("role", staff.getRole());
        data.put("status", staff.getStatus());
        data.put("dailyLimit", limit);
        data.put("todayUsed", used);
        data.put("remaining", Math.max(0, limit - used));
        data.put("storeId", staff.getStoreId());
        data.put("storeName", store == null ? "" : store.getName());
        data.put("storeAddress", store == null ? "" : store.getAddress());
        data.put("storePhone", store == null ? "" : store.getPhone());
        data.put("businessHours", store == null ? "" : store.getBusinessHours());
        data.put("tenantId", staff.getTenantId());
        data.put("tenantName", tenant == null ? "" : tenant.getName());
        data.put("todayGenerations", count("SELECT COUNT(*) FROM generation_task "
                + "WHERE staff_id=? AND source='employee' AND created_at>=CURDATE()", staff.getId()));
        data.put("claimedCount", count("SELECT COUNT(*) FROM generation_task "
                + "WHERE staff_id=? AND source='employee' AND claimed_user_id>0", staff.getId()));
        data.put("recentTasks", jdbc.queryForList(
                "SELECT id, status, progress, created_at AS createdAt, claimed_user_id AS claimedUserId, "
                        + "claim_token AS claimToken FROM generation_task "
                        + "WHERE staff_id=? AND source='employee' ORDER BY id DESC LIMIT 10", staff.getId()));
        return data;
    }

    public Map<String, Object> generate(Long userId, Map<String, Object> body) {
        Staff staff = requireStaff(userId);
        Store store = storeMapper.selectById(staff.getStoreId());
        if (store == null || store.getStatus() == null || store.getStatus() != 1) {
            throw new BizException("门店已停用，请联系店长");
        }
        if (!Boolean.TRUE.equals(body.get("consent"))) {
            throw new BizException("请先确认顾客已同意使用照片进行本次 AI 预览");
        }
        consumeQuota(staff);
        try {
            String photoUrl = str(body.get("photoUrl"));
            String gender = str(body.get("gender"));
            List<String> styleIds = styleIdsOf(body.get("styleIds"));
            String claimToken = UUID.randomUUID().toString().replace("-", "");
            GenerationTask task = generationService.createEmployee(
                    photoUrl, gender, styleIds, staff.getStoreId(), staff.getId(), claimToken);
            processor.process(task.getId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", task.getId());
            data.put("claimToken", claimToken);
            data.put("status", task.getStatus());
            data.put("quota", quotaMap(staff));
            return data;
        } catch (RuntimeException e) {
            releaseQuota(staff.getId());
            throw e;
        }
    }

    public Map<String, Object> task(Long userId, Long taskId) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null || !"employee".equals(task.getSource())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "试发记录不存在");
        }
        boolean owner = task.getUserId() != null && task.getUserId().equals(userId);
        boolean claimed = task.getClaimedUserId() != null && task.getClaimedUserId().equals(userId);
        if (!owner && !claimed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "试发记录不存在");
        }
        return generationService.toMap(task);
    }

    public Map<String, Object> claim(Long userId, Long taskId, String token) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null || !"employee".equals(task.getSource())) {
            throw new BizException("试发记录不存在");
        }
        if (token == null || token.isBlank() || !token.equals(task.getClaimToken())) {
            throw new BizException("领取凭证无效");
        }
        if (task.getExpireAt() != null && task.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException("该试发结果已过期，请让员工重新生成");
        }
        if (task.getClaimedUserId() != null && task.getClaimedUserId() > 0
                && !task.getClaimedUserId().equals(userId)) {
            throw new BizException("该试发结果已被其他账号领取");
        }
        if (task.getClaimedUserId() == null || task.getClaimedUserId() == 0) {
            taskMapper.update(null, new UpdateWrapper<GenerationTask>()
                    .eq("id", taskId)
                    .eq("claimed_user_id", 0)
                    .set("claimed_user_id", userId)
                    .set("claimed_at", LocalDateTime.now()));
            task.setClaimedUserId(userId);
            task.setClaimedAt(LocalDateTime.now());
        }
        Map<String, Object> data = generationService.toMap(task);
        Store store = storeMapper.selectById(task.getStoreId());
        Staff staff = staffMapper.selectById(task.getStaffId());
        data.put("storeName", store == null ? "" : store.getName());
        data.put("storeAddress", store == null ? "" : store.getAddress());
        data.put("staffName", staff == null ? "" : staff.getName());
        return data;
    }

    public Map<String, Object> quota(Long userId) {
        Staff staff = requireStaff(userId);
        return quotaMap(staff);
    }

    private Map<String, Object> quotaMap(Staff staff) {
        int used = usedToday(staff.getId());
        int limit = staff.getDailyLimit() == null ? 20 : staff.getDailyLimit();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dailyLimit", limit);
        data.put("todayUsed", used);
        data.put("remaining", Math.max(0, limit - used));
        return data;
    }

    private Staff activeStaff(Long userId) {
        return staffMapper.selectOne(new QueryWrapper<Staff>()
                .eq("user_id", userId).eq("status", 1).last("limit 1"));
    }

    private Staff requireStaff(Long userId) {
        Staff staff = activeStaff(userId);
        if (staff == null) {
            throw new BizException("请先在员工工作台绑定员工身份");
        }
        return staff;
    }

    private void consumeQuota(Staff staff) {
        int limit = staff.getDailyLimit() == null ? 20 : staff.getDailyLimit();
        jdbc.update("INSERT INTO staff_quota (staff_id, quota_date, used, updated_at) "
                        + "VALUES (?, CURDATE(), 0, NOW()) ON DUPLICATE KEY UPDATE "
                        + "used = IF(quota_date = CURDATE(), used, 0), quota_date = CURDATE(), updated_at = NOW()",
                staff.getId());
        int rows = jdbc.update("UPDATE staff_quota SET used = used + 1, updated_at = NOW() "
                        + "WHERE staff_id = ? AND quota_date = CURDATE() AND used < ?",
                staff.getId(), limit);
        if (rows == 0) {
            throw new BizException("今日免费试发次数已用完（" + limit + " 次），请明天再试");
        }
    }

    private void releaseQuota(Long staffId) {
        jdbc.update("UPDATE staff_quota SET used = GREATEST(used - 1, 0), updated_at = NOW() "
                + "WHERE staff_id = ? AND quota_date = CURDATE()", staffId);
    }

    private int usedToday(Long staffId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT used, quota_date AS quotaDate FROM staff_quota WHERE staff_id=?", staffId);
        if (rows.isEmpty()) {
            return 0;
        }
        Object date = rows.get(0).get("quotaDate");
        if (date == null || !date.toString().equals(java.time.LocalDate.now().toString())) {
            return 0;
        }
        Object used = rows.get(0).get("used");
        return used instanceof Number n ? n.intValue() : 0;
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    @SuppressWarnings("unchecked")
    private List<String> styleIdsOf(Object raw) {
        List<String> list = new ArrayList<>();
        if (raw instanceof List<?> items) {
            for (Object o : items) {
                if (o == null) {
                    continue;
                }
                String id = String.valueOf(o).trim();
                if (id.matches("[A-Za-z0-9_-]{1,32}") && !list.contains(id)) {
                    list.add(id);
                }
            }
        }
        return list;
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
