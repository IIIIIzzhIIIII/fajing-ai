package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.AiCallLog;
import com.fajing.entity.Comment;
import com.fajing.entity.GenerationTask;
import com.fajing.entity.Hairstyle;
import com.fajing.entity.Order;
import com.fajing.entity.Post;
import com.fajing.entity.Report;
import com.fajing.mapper.AiCallLogMapper;
import com.fajing.mapper.CommentMapper;
import com.fajing.mapper.GenerationTaskMapper;
import com.fajing.mapper.HairstyleMapper;
import com.fajing.mapper.OrderMapper;
import com.fajing.mapper.PostMapper;
import com.fajing.mapper.ReportMapper;
import com.fajing.service.AdminLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 管理端接口（由 AdminInterceptor 校验 X-Admin-Token）
 * TODO: 接入 RuoYi 权限体系
 */
@Tag(name = "管理端 · 内容与订单", description = "发型管理、帖子/评论审核、举报处理与订单查看")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final HairstyleMapper hairstyleMapper;
    private final PostMapper postMapper;
    private final OrderMapper orderMapper;
    private final CommentMapper commentMapper;
    private final ReportMapper reportMapper;
    private final AdminLogService adminLogService;
    private final GenerationTaskMapper taskMapper;
    private final AiCallLogMapper aiCallLogMapper;

    @Operation(summary = "今日运营数据（生成量、成本、成功率、付费转化）")
    @GetMapping("/stats/today")
    public Result<Map<String, Object>> todayStats() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Long generateCount = taskMapper.selectCount(new QueryWrapper<GenerationTask>()
                .ge("created_at", start));
        Long successCount = taskMapper.selectCount(new QueryWrapper<GenerationTask>()
                .eq("status", "success").ge("created_at", start));
        Long failedCount = taskMapper.selectCount(new QueryWrapper<GenerationTask>()
                .eq("status", "failed").ge("created_at", start));
        Long aiCalls = aiCallLogMapper.selectCount(new QueryWrapper<AiCallLog>()
                .ge("created_at", start));
        long costCents = sumOf(aiCallLogMapper.selectMaps(new QueryWrapper<AiCallLog>()
                .select("IFNULL(SUM(cost_cents),0) AS total").ge("created_at", start)));
        Long paidOrders = orderMapper.selectCount(new QueryWrapper<Order>()
                .eq("status", "paid").ge("created_at", start));
        long payingUsers = sumOf(orderMapper.selectMaps(new QueryWrapper<Order>()
                .select("COUNT(DISTINCT user_id) AS total")
                .eq("status", "paid").ge("created_at", start)));
        long generateUsers = sumOf(taskMapper.selectMaps(new QueryWrapper<GenerationTask>()
                .select("COUNT(DISTINCT user_id) AS total").ge("created_at", start)));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generateCount", generateCount == null ? 0 : generateCount);
        data.put("successCount", successCount == null ? 0 : successCount);
        data.put("failedCount", failedCount == null ? 0 : failedCount);
        data.put("successRate", rate(successCount, generateCount));
        data.put("aiCalls", aiCalls == null ? 0 : aiCalls);
        data.put("costCents", costCents);
        data.put("costYuan", String.format("%.2f", costCents / 100.0));
        data.put("paidOrders", paidOrders == null ? 0 : paidOrders);
        data.put("payingUsers", payingUsers);
        data.put("generateUsers", generateUsers);
        data.put("conversionRate", generateUsers == 0 ? 0 : Math.round(payingUsers * 1000.0 / generateUsers) / 10.0);
        return Result.ok(data);
    }

    private long sumOf(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        Object total = rows.get(0).get("total");
        if (total == null) {
            return 0L;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(total)).longValue();
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private double rate(Long part, Long all) {
        if (part == null || all == null || all == 0) {
            return 0;
        }
        return Math.round(part * 1000.0 / all) / 10.0;
    }

    @Operation(summary = "发型列表（管理端）")
    @GetMapping("/hairstyles")
    public Result<List<Hairstyle>> hairstyles(@RequestParam(required = false) String gender) {
        QueryWrapper<Hairstyle> wrapper = new QueryWrapper<Hairstyle>().orderByAsc("sort");
        if (gender != null && !gender.isBlank()) {
            wrapper.eq("gender", gender);
        }
        return Result.ok(hairstyleMapper.selectList(wrapper));
    }

    @Operation(summary = "新增发型")
    @PostMapping("/hairstyles")
    public Result<Hairstyle> create(@RequestBody Hairstyle hairstyle) {
        if (hairstyle.getName() == null || hairstyle.getName().isBlank()) {
            throw new BizException("发型名称不能为空");
        }
        if (hairstyle.getId() == null || hairstyle.getId().isBlank()) {
            hairstyle.setId((("male".equals(hairstyle.getGender()) ? "m" : "f")
                    + UUID.randomUUID().toString().substring(0, 6)));
        }
        if (hairstyle.getStatus() == null) {
            hairstyle.setStatus(1);
        }
        if (hairstyle.getSort() == null || hairstyle.getSort() == 0) {
            hairstyle.setSort(99);
        }
        hairstyle.setCreatedAt(LocalDateTime.now());
        hairstyleMapper.insert(hairstyle);
        adminLogService.log("CREATE_HAIRSTYLE", hairstyle.getId());
        return Result.ok(hairstyle);
    }

    @Operation(summary = "编辑发型")
    @PutMapping("/hairstyles/{id}")
    public Result<Hairstyle> update(@PathVariable String id, @RequestBody Hairstyle hairstyle) {
        Hairstyle exists = hairstyleMapper.selectById(id);
        if (exists == null) {
            throw new BizException("发型不存在");
        }
        hairstyle.setId(id);
        hairstyle.setCreatedAt(exists.getCreatedAt());
        hairstyleMapper.updateById(hairstyle);
        adminLogService.log("UPDATE_HAIRSTYLE", id);
        return Result.ok(hairstyleMapper.selectById(id));
    }

    @Operation(summary = "上架/下架发型")
    @PatchMapping("/hairstyles/{id}/status")
    public Result<Hairstyle> status(@PathVariable String id, @RequestParam Integer status) {
        Hairstyle exists = hairstyleMapper.selectById(id);
        if (exists == null) {
            throw new BizException("发型不存在");
        }
        exists.setStatus(status != null && status == 1 ? 1 : 0);
        hairstyleMapper.updateById(exists);
        adminLogService.log("HAIRSTYLE_STATUS", id + ":" + exists.getStatus());
        return Result.ok(exists);
    }

    @Operation(summary = "帖子列表（含评论明细）")
    @GetMapping("/posts")
    public Result<List<Map<String, Object>>> posts() {
        List<Post> posts = postMapper.selectList(new QueryWrapper<Post>().orderByDesc("created_at"));
        return Result.ok(posts.stream().map(this::postMap).collect(Collectors.toList()));
    }

    /** 帖子带上评论明细，管理端能直接看到用户最新评论 */
    private Map<String, Object> postMap(Post p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
        map.put("userId", p.getUserId());
        map.put("name", p.getName());
        map.put("avatarColor", p.getAvatarColor());
        map.put("meta", p.getMeta());
        map.put("badge", p.getBadge());
        map.put("gender", p.getGender());
        map.put("styleName", p.getStyleName());
        map.put("content", p.getContent());
        map.put("aiImage", p.getAiImage());
        map.put("afterImage", p.getAfterImage());
        map.put("score", p.getScore());
        map.put("likes", p.getLikes());
        map.put("createdAt", p.getCreatedAt());

        List<Comment> comments = commentMapper.selectList(new QueryWrapper<Comment>()
                .eq("post_id", p.getId()).orderByAsc("created_at"));
        map.put("commentCount", comments.size());
        map.put("comments", comments.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("userName", c.getUserName());
            item.put("content", c.getContent());
            item.put("createdAt", c.getCreatedAt());
            return item;
        }).collect(Collectors.toList()));
        return map;
    }

    @Operation(summary = "删除帖子")
    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        postMapper.deleteById(id);
        adminLogService.log("DELETE_POST", "post:" + id);
        return Result.ok();
    }

    /** 举报列表（含被举报帖子的标题与内容） */
    @Operation(summary = "举报列表")
    @GetMapping("/reports")
    public Result<List<Map<String, Object>>> reports() {
        List<Report> list = reportMapper.selectList(new QueryWrapper<Report>().orderByDesc("created_at"));
        return Result.ok(list.stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("postId", r.getPostId());
            map.put("reason", r.getReason());
            map.put("status", r.getStatus());
            map.put("createdAt", r.getCreatedAt());
            Post post = postMapper.selectById(r.getPostId());
            map.put("postTitle", post == null ? "帖子已删除" : post.getStyleName());
            map.put("postContent", post == null ? "" : post.getContent());
            return map;
        }).collect(Collectors.toList()));
    }

    /** 处理举报：handled=已处理，ignored=忽略 */
    @Operation(summary = "处理举报（handled/ignored）")
    @PatchMapping("/reports/{id}/status")
    public Result<Void> handleReport(@PathVariable Long id, @RequestParam String status) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BizException("举报不存在");
        }
        report.setStatus("handled".equals(status) ? "handled" : "ignored");
        reportMapper.updateById(report);
        adminLogService.log("REPORT_" + report.getStatus().toUpperCase(), "report:" + id);
        return Result.ok();
    }

    @Operation(summary = "删除评论")
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        commentMapper.deleteById(id);
        adminLogService.log("DELETE_COMMENT", "comment:" + id);
        return Result.ok();
    }

    @Operation(summary = "订单列表")
    @GetMapping("/orders")
    public Result<List<Order>> orders() {
        return Result.ok(orderMapper.selectList(new QueryWrapper<Order>().orderByDesc("created_at")));
    }
}
