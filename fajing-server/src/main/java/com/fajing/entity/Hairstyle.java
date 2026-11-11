package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hairstyle")
public class Hairstyle {
    private String id;
    private String name;
    private String gender;
    private String cat;
    private String len;
    private String face;
    private String en;
    private String imgUrl;
    private Integer status;
    private Integer sort;
    private LocalDateTime createdAt;
}
