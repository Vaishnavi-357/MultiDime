package com.multidime.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * The title and description shown at the top of the Swagger page (/swagger-ui.html).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI talentVectorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("MultiDime API")
                .version("1.0.0")
                .description("""
                        Backend for MultiDime: a chatbot that answers from your own PDFs (RAG), \
                        a resume reviewer, a resume-to-job-description matcher (ATS score) and a history of \
                        everything that was asked. The AI is Google Gemini; history is stored in MySQL.

                        Every error comes back as JSON in the form {"error": "message"}. \
                        The AI endpoints use your Gemini quota, so the free tier can return a 500 with a \
                        rate-limit message when the daily limit is used up."""));
    }
}
