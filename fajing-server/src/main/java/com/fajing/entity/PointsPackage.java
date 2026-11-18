package com.fajing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 充值套餐：价格、点数、会员天数由后台维护，服务端以此定价 */
@Data
@TableName("points_package")
public class PointsPackage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String description;
    private String badge;
    /** points / vip / vvip */
    private String bizType;
    private BigDecimal price;
    /** biz_type=points 时到账的魅力值 */
    private Integer points;
    /** biz_type=vip 时开通的天数 */
    private Integer days;
    /** 1 上架 / 0 下架 */
    private Integer status;
    private Integer sort;
}
