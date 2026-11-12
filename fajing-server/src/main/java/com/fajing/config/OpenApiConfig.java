package com.fajing.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档：/swagger-ui/index.html，JSON 在 /v3/api-docs
 * C 端接口用用户 JWT，管理端接口用管理员 JWT，均通过 Authorize 填 Bearer
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fajingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("发镜 AI API")
                        .version("1.0.0")
                        .description("换发型生成、试发记录、讨论区、订单支付与管理后台接口"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
