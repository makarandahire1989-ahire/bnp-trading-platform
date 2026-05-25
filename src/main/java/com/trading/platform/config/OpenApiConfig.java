package com.trading.platform.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tradingPlatformOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BNP Trading Platform API")
                        .description("""
                                High-speed equity-trading platform implementing the reserve → pay → confirm workflow.
                                
                                ## Key behaviours
                                - **Inventory integrity**: pessimistic locking prevents overselling even under heavy concurrent load.
                                - **Reservation window**: each pending order holds stock for a configurable window (default 15 min); \
                                  inventory is released automatically on expiry.
                                - **Idempotency**: re-confirming an already-confirmed order is rejected with 422.
                                - **Observability**: every response carries a `correlationId` and `timestamp` for support triage.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Trading Platform Team"))
                        .license(new License()
                                .name("Internal Use Only")));
    }
}
