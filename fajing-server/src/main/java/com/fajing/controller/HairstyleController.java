package com.fajing.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fajing.common.BizException;
import com.fajing.common.Result;
import com.fajing.entity.HairColor;
import com.fajing.entity.Hairstyle;
import com.fajing.mapper.HairColorMapper;
import com.fajing.mapper.HairstyleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "发型与发色", description = "发型库浏览与发色列表")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HairstyleController {

    private final HairstyleMapper hairstyleMapper;
    private final HairColorMapper hairColorMapper;

    @Operation(summary = "发型列表")
    @GetMapping("/hairstyles")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) String gender,
                                                  @RequestParam(required = false) String cat) {
        String g = "male".equals(gender) ? "male" : (gender == null || gender.isBlank() ? null : "female");
        QueryWrapper<Hairstyle> wrapper = new QueryWrapper<Hairstyle>().eq("status", 1);
        if (g != null) {
            wrapper.eq("gender", g);
        }
        if (cat != null && !cat.isBlank() && !"全部".equals(cat)) {
            wrapper.eq("cat", cat);
        }
        wrapper.orderByAsc("sort");
        List<Hairstyle> list = hairstyleMapper.selectList(wrapper);
        return Result.ok(list.stream().map(this::toMap).collect(Collectors.toList()));
    }

    @Operation(summary = "发色列表")
    @GetMapping("/colors")
    public Result<List<HairColor>> colors() {
        return Result.ok(hairColorMapper.selectList(new QueryWrapper<HairColor>().orderByAsc("sort")));
    }

    private Map<String, Object> toMap(Hairstyle s) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", s.getId());
        map.put("name", s.getName());
        map.put("gender", s.getGender());
        map.put("cat", s.getCat());
        map.put("len", s.getLen());
        map.put("face", s.getFace());
        map.put("en", s.getEn());
        map.put("img", img(s));
        map.put("status", s.getStatus());
        return map;
    }

    /** img_url 为空时按英文提示词生成占位效果图地址 */
    private String img(Hairstyle s) {
        if (s.getImgUrl() != null && !s.getImgUrl().isBlank()) {
            return s.getImgUrl();
        }
        if (s.getEn() == null || s.getEn().isBlank()) {
            throw new BizException("发型数据缺少图片描述");
        }
        String person = "male".equals(s.getGender()) ? "young asian man" : "young asian woman";
        String prompt = person + ", " + s.getEn()
                + ", front facing portrait, looking straight at camera, head level and centered, "
                + "symmetrical frontal face, no side profile, no tilted head, "
                + "shoulders up, natural window light, clean neutral background, photorealistic, 85mm lens, high detail";
        return "https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt="
                + URLEncoder.encode(prompt, StandardCharsets.UTF_8) + "&image_size=portrait_4_3";
    }
}
