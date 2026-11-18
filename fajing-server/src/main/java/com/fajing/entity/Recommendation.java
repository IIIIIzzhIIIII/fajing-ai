package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 一次人脸分析对应的发型/发色排名结果 */
@Data
@TableName("recommendation")
public class Recommendation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String photoUrl;
    private String gender;
    private String faceShape;
    private String skinTone;
    private String stylesJson;
    private String colorsJson;
    private LocalDateTime createdAt;
}
