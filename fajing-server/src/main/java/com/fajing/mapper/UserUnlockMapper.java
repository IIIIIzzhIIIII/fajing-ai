package com.fajing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fajing.entity.UserUnlock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface UserUnlockMapper extends BaseMapper<UserUnlock> {

    /** 热门发型：按周期内解锁数排序，关联发型名称 */
    @Select("""
            SELECT u.style_id AS styleId,
                   COALESCE(h.name, u.style_id) AS name,
                   COUNT(*) AS unlocks
            FROM user_unlock u
            LEFT JOIN hairstyle h ON h.id = u.style_id
            WHERE u.created_at >= #{start} AND u.created_at < #{end}
            GROUP BY u.style_id, h.name
            ORDER BY unlocks DESC
            LIMIT 5
            """)
    List<Map<String, Object>> hotStyles(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
