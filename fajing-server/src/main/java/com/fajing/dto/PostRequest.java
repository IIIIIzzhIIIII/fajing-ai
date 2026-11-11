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
    private List<String> tags;
}
