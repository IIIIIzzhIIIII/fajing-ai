package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("generation_task")
public class GenerationTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String photoUrl;
    private String gender;
    private String mode;
    private String styleIds;
    private String colorId;
    private String status;
    private Integer progress;
    private String resultJson;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
}
