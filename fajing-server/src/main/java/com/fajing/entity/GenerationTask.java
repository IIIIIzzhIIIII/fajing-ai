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
    /** 本批是否使用免费额度：1 免费（前 3 款预生成并解锁），0 非免费批次 */
    private Integer freeQuota;
    /** 推荐记录 ID：用于把脸型排名、适配分与推荐依据带回结果页 */
    private Long recommendId;
    /** 1 = 创建后立即生成（发色变体），生成成功即解锁；0 = 免费批次/等待解锁 */
    private Integer autoGen;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
}
