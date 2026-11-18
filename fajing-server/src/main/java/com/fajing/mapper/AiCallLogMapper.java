package com.fajing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fajing.entity.AiCallLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AiCallLogMapper extends BaseMapper<AiCallLog> {

    /** 按用户汇总时间范围内的生图成本（分），用于把成本归因到渠道并从净实收中扣除 */
    @Select("""
            SELECT user_id AS userId, IFNULL(SUM(cost_cents), 0) AS costCents
            FROM ai_call_log
            WHERE created_at >= #{start} AND created_at < #{end}
            GROUP BY user_id
            """)
    List<Map<String, Object>> costByUser(@Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);

    /** 按用户 + 日期汇总生图成本（分），用于看板趋势逐日扣减 */
    @Select("""
            SELECT user_id AS userId, DATE(created_at) AS day, IFNULL(SUM(cost_cents), 0) AS costCents
            FROM ai_call_log
            WHERE created_at >= #{start} AND created_at < #{end}
            GROUP BY user_id, DATE(created_at)
            """)
    List<Map<String, Object>> costByUserDay(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);
}
