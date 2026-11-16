package com.fajing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fajing.entity.EventLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface EventLogMapper extends BaseMapper<EventLog> {

    /** 按渠道聚合埋点：渠道取 payload.channel（小程序 / H5），缺失记为 unknown */
    @Select("""
            SELECT COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.channel')), ''), 'unknown') AS channel,
                   COUNT(*) AS totalEvents,
                   COUNT(DISTINCT user_id) AS visitUsers,
                   COUNT(DISTINCT CASE WHEN `event` = 'result_view' THEN user_id END) AS viewUsers,
                   SUM(`event` = 'generate_success') AS generateSuccess,
                   SUM(`event` = 'save_image') AS saveImages,
                   SUM(`event` IN ('share', 'share_click')) AS shareEvents
            FROM event_log
            WHERE created_at >= #{start}
            GROUP BY COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.channel')), ''), 'unknown')
            """)
    List<Map<String, Object>> statsByChannel(@Param("start") LocalDateTime start);

    /** 每个用户在时间范围内的最近一次渠道，用于把订单/新客归因到渠道 */
    @Select("""
            SELECT user_id AS userId,
                   COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.channel')), ''), 'unknown') AS channel,
                   MAX(created_at) AS lastAt
            FROM event_log
            WHERE created_at >= #{start}
            GROUP BY user_id, COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(payload_json, '$.channel')), ''), 'unknown')
            """)
    List<Map<String, Object>> userChannels(@Param("start") LocalDateTime start);
}
