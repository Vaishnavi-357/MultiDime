package com.multidime.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.OpenAPI;

class OpenApiConfigTest {

    @Test
    @DisplayName("The API documentation has a title, a version and a description")
    void apiInfoIsFilledIn() {
        OpenAPI api = new OpenApiConfig().talentVectorOpenApi();

        assertThat(api.getInfo().getTitle()).isEqualTo("MultiDime API");
        assertThat(api.getInfo().getVersion()).isNotBlank();
        assertThat(api.getInfo().getDescription()).contains("Gemini").contains("MySQL");
    }
}
