package com.gestor.financeiro.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI financeiroOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Gestor Financeiro API")
                .version("v1")
                .description("API REST para gestão financeira pessoal (web e mobile)")
                .contact(new Contact().name("Equipe Gestor Financeiro"))
                .license(new License().name("Proprietary"))
            );
    }
}
