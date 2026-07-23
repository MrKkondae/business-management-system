package com.bms.backend.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ApiErrorCodeCatalogTests {

    @Test
    void implementationMatchesErrorCatalog() throws IOException {
        Path catalogPath = Path.of(
                "..",
                "docs",
                "03.application-development",
                "02.design",
                "02.catalog",
                "error-codes.csv");
        Map<String, String[]> catalog = Files.readAllLines(catalogPath, StandardCharsets.UTF_8)
                .stream()
                .skip(1)
                .filter(line -> !line.isBlank())
                .map(line -> line.split(",", -1))
                .collect(Collectors.toMap(columns -> columns[0], Function.identity()));

        assertThat(Arrays.stream(ApiErrorCode.values()).map(Enum::name))
                .containsExactlyInAnyOrderElementsOf(catalog.keySet());
        for (ApiErrorCode errorCode : ApiErrorCode.values()) {
            String[] row = catalog.get(errorCode.name());
            assertThat(errorCode.status().value()).isEqualTo(Integer.parseInt(row[2]));
            assertThat(errorCode.userMessage()).isEqualTo(row[3]);
        }
    }
}
