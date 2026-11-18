package com.fajing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.entity.PointsPackage;
import com.fajing.mapper.PointsPackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** 充值套餐：价格与权益由后台维护，下单时以库中配置为准 */
@Service
@RequiredArgsConstructor
public class PointsPackageService {

    private static final Set<String> BIZ_TYPES = Set.of("points", "vip", "vvip");

    private final PointsPackageMapper packageMapper;

    public List<PointsPackage> listAll() {
        return packageMapper.selectList(new QueryWrapper<PointsPackage>()
                .orderByAsc("sort").orderByAsc("id"));
    }

    public List<PointsPackage> listEnabled() {
        return packageMapper.selectList(new QueryWrapper<PointsPackage>()
                .eq("status", 1).orderByAsc("sort").orderByAsc("id"));
    }

    /** 下单只接受上架套餐，避免前端传任意价格 */
    public PointsPackage findEnabled(Long id) {
        PointsPackage pkg = id == null ? null : packageMapper.selectById(id);
        if (pkg == null || pkg.getStatus() == null || pkg.getStatus() != 1) {
            throw new BizException("套餐不存在或已下架");
        }
        return pkg;
    }

    public PointsPackage save(PointsPackage req) {
        String bizType = req.getBizType() == null ? "" : req.getBizType().trim();
        if (!BIZ_TYPES.contains(bizType)) {
            throw new BizException("套餐类型只能是 points / vip / vvip");
        }
        if (req.getPrice() == null || req.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException("套餐价格必须大于 0");
        }
        if ("points".equals(bizType) && (req.getPoints() == null || req.getPoints() <= 0)) {
            throw new BizException("魅力值套餐必须填写到账点数");
        }
        if ("vip".equals(bizType) && (req.getDays() == null || req.getDays() <= 0)) {
            throw new BizException("VIP 套餐必须填写有效天数");
        }
        req.setBizType(bizType);
        req.setTitle(req.getTitle() == null ? "" : req.getTitle().trim());
        req.setDescription(req.getDescription() == null ? "" : req.getDescription().trim());
        req.setBadge(req.getBadge() == null ? "" : req.getBadge().trim());
        req.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        req.setSort(req.getSort() == null ? 0 : req.getSort());
        if (req.getId() == null) {
            packageMapper.insert(req);
        } else {
            if (packageMapper.selectById(req.getId()) == null) {
                throw new BizException("套餐不存在");
            }
            packageMapper.updateById(req);
        }
        return packageMapper.selectById(req.getId());
    }

    /** 下架而非物理删除，避免历史订单引用断链 */
    public void disable(Long id) {
        PointsPackage pkg = packageMapper.selectById(id);
        if (pkg == null) {
            throw new BizException("套餐不存在");
        }
        pkg.setStatus(0);
        packageMapper.updateById(pkg);
    }
}
