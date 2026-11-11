package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.Hairstyle;
import com.fajing.entity.Order;
import com.fajing.entity.Post;
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
import java.util.List;
import java.util.UUID;

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
    public Result<List<Post>> posts() {
        return Result.ok(postMapper.selectList(new QueryWrapper<Post>().orderByDesc("created_at")));
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
