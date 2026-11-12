package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.Comment;
import com.fajing.entity.Hairstyle;
import com.fajing.entity.Order;
import com.fajing.entity.Post;
import com.fajing.mapper.CommentMapper;
import com.fajing.mapper.HairstyleMapper;
import com.fajing.mapper.OrderMapper;
import com.fajing.mapper.PostMapper;
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
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final HairstyleMapper hairstyleMapper;
    private final PostMapper postMapper;
    private final OrderMapper orderMapper;
    private final CommentMapper commentMapper;

    @GetMapping("/hairstyles")
    public Result<List<Hairstyle>> hairstyles(@RequestParam(required = false) String gender) {
        QueryWrapper<Hairstyle> wrapper = new QueryWrapper<Hairstyle>().orderByAsc("sort");
        if (gender != null && !gender.isBlank()) {
            wrapper.eq("gender", gender);
        }
        return Result.ok(hairstyleMapper.selectList(wrapper));
    }

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
        return Result.ok(hairstyle);
    }

    @PutMapping("/hairstyles/{id}")
    public Result<Hairstyle> update(@PathVariable String id, @RequestBody Hairstyle hairstyle) {
        Hairstyle exists = hairstyleMapper.selectById(id);
        if (exists == null) {
            throw new BizException("发型不存在");
        }
        hairstyle.setId(id);
        hairstyle.setCreatedAt(exists.getCreatedAt());
        hairstyleMapper.updateById(hairstyle);
        return Result.ok(hairstyleMapper.selectById(id));
    }

    @PatchMapping("/hairstyles/{id}/status")
    public Result<Hairstyle> status(@PathVariable String id, @RequestParam Integer status) {
        Hairstyle exists = hairstyleMapper.selectById(id);
        if (exists == null) {
            throw new BizException("发型不存在");
        }
        exists.setStatus(status != null && status == 1 ? 1 : 0);
        hairstyleMapper.updateById(exists);
        return Result.ok(exists);
    }

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

    @DeleteMapping("/posts/{id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        postMapper.deleteById(id);
        return Result.ok();
    }

    @GetMapping("/orders")
    public Result<List<Order>> orders() {
        return Result.ok(orderMapper.selectList(new QueryWrapper<Order>().orderByDesc("created_at")));
    }
}
