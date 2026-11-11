package com.fajing.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class GenerateRequest {
    private String photoUrl;

    private String gender = "female";

    private String mode = "hair";

    private String colorId = "";

    @NotEmpty(message = "请先选择发型")
    private List<String> styleIds;
}
