package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_user")
public class AppUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String nickname;
    private String avatar;
    private String gender;
    private String openid;
    private String phone;
    private String role;
    private String sessionKey;
    private String passwordHash;
    private Integer status;
    private LocalDateTime registerAt;
    private LocalDateTime lastActiveAt;
}
