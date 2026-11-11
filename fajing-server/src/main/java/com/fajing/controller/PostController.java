package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.dto.PostRequest;
import com.fajing.entity.Comment;
import com.fajing.entity.Post;
import com.fajing.mapper.CommentMapper;
import com.fajing.mapper.PostMapper;
import com.fajing.service.GenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PostController {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;

    @GetMapping("/posts")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String filter) {
        QueryWrapper<Post> wrapper = new QueryWrapper<Post>().orderByDesc("created_at");
        String f = filter == null ? "" : filter;
        switch (f) {
            case "真实作业" -> wrapper.eq("badge", "real");
            case "求建议" -> wrapper.eq("badge", "ask");
            case "女款" -> wrapper.eq("gender", "female");
            case "男款" -> wrapper.eq("gender", "male");
            case "圆脸友好" -> wrapper.like("tags", "圆脸");
            default -> {
            }
        }
        List<Post> posts = postMapper.selectList(wrapper);
        return Result.ok(posts.stream().map(this::toMap).collect(Collectors.toList()));
    }

    @PostMapping("/posts")
    public Result<Map<String, Object>> create(@RequestBody PostRequest req) {
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new BizException("说说剪完的感受吧");
        }
        Post post = new Post();
        post.setUserId(GenerationService.USER_ID);
        post.setName("我");
        post.setAvatarColor("#C95676");
        post.setMeta("刚刚 · 我的作业");
        post.setBadge("real");
        post.setGender("male".equals(req.getGender()) ? "male" : "female");
        post.setStyleName(req.getStyleName() == null ? "" : req.getStyleName());
        post.setContent(req.getContent());
        post.setAiImage(req.getAiImage() == null ? "" : req.getAiImage());
        post.setAfterImage(req.getAfterImage() == null ? "" : req.getAfterImage());
        post.setTags(req.getTags() == null ? "" : String.join(",", req.getTags()));
        post.setLikes(0);
        post.setLiked(false);
        post.setCreatedAt(LocalDateTime.now());
        postMapper.insert(post);
        return Result.ok(toMap(post));
    }

    @PostMapping("/posts/{id}/like")
    public Result<Map<String, Object>> like(@PathVariable Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BizException("帖子不存在");
        }
        boolean liked = !Boolean.TRUE.equals(post.getLiked());
        int likes = post.getLikes() == null ? 0 : post.getLikes();
        post.setLiked(liked);
        post.setLikes(Math.max(0, likes + (liked ? 1 : -1)));
        postMapper.updateById(post);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("liked", liked);
        data.put("likes", post.getLikes());
        return Result.ok(data);
    }

    @GetMapping("/posts/{id}/comments")
    public Result<List<Map<String, Object>>> comments(@PathVariable Long id) {
        List<Comment> list = commentMapper.selectList(new QueryWrapper<Comment>()
                .eq("post_id", id).orderByAsc("created_at"));
        return Result.ok(list.stream().map(this::commentMap).collect(Collectors.toList()));
    }

    @PostMapping("/posts/{id}/comments")
    public Result<Map<String, Object>> addComment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BizException("帖子不存在");
        }
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new BizException("评论内容不能为空");
        }
        Comment comment = new Comment();
        comment.setPostId(id);
        comment.setUserName("我");
        comment.setContent(content.trim());
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        return Result.ok(commentMap(comment));
    }

    private Map<String, Object> commentMap(Comment c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("userName", c.getUserName());
        map.put("content", c.getContent());
        map.put("createdAt", c.getCreatedAt());
        return map;
    }

    private Map<String, Object> toMap(Post p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());
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
        map.put("tags", p.getTags() == null || p.getTags().isBlank()
                ? new ArrayList<String>()
                : Arrays.asList(p.getTags().split(",")));
        map.put("likes", p.getLikes());
        map.put("liked", Boolean.TRUE.equals(p.getLiked()));
        map.put("createdAt", p.getCreatedAt());
        map.put("comments", commentMapper.selectList(new QueryWrapper<Comment>()
                        .eq("post_id", p.getId()).orderByAsc("created_at"))
                .stream().map(this::commentMap).collect(Collectors.toList()));
        return map;
    }
}
