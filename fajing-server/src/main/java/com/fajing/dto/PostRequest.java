package com.fajing.dto;

import lombok.Data;

import java.util.List;

@Data
public class PostRequest {
    private String styleName = "";
    private String content = "";
    private String aiImage = "";
    private String afterImage = "";
    private String gender = "female";
    /** real=剪后作业，ask=还没剪求建议 */
    private String badge = "real";
    private List<String> tags;
}
