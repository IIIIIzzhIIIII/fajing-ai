package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post")
public class Post {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String avatarColor;
    private String meta;
    private String badge;
    private String gender;
    private String styleName;
    private String content;
    private String aiImage;
    private String afterImage;
    private Integer score;
    private String tags;
    private Integer likes;
    private Boolean liked;
    private LocalDateTime createdAt;
}
