package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("event_log")
public class EventLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String event;
    private String payloadJson;
    private LocalDateTime createdAt;
}
