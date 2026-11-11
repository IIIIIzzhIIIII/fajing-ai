package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("hair_color")
public class HairColor {
    private String id;
    private String name;
    private String en;
    private String bg;
    private Integer sort;
}
