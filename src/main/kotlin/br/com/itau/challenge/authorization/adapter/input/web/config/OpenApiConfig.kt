package br.com.itau.challenge.authorization.adapter.input.web.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun authorizationOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Autorização de Transações — Desafio Técnico Itaú")
                    .description(
                        "API de autorização de transações financeiras (crédito e débito), " +
                            "seguindo arquitetura hexagonal. Consulte o endpoint " +
                            "POST /transactions/{transactionId} para autorizar uma transação.",
                    )
                    .version("v1")
                    .contact(Contact().name("Itaú Code Challenge"))
                    .license(License().name("Uso interno / desafio técnico")),
            )
}
