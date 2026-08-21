package com.pharmasense.catalog.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogCodeGeneratorTest {

    private final CatalogCodeGenerator generator = new CatalogCodeGenerator();

    @Test
    void itemCodesUseTheItemPrefix() {
        assertThat(generator.generateItemCode()).startsWith("ITM-");
    }

    @Test
    void batchCodesUseTheBatchPrefix() {
        assertThat(generator.generateBatchCode()).startsWith("BAT-");
    }

    @Test
    void generatedCodesAreHighlyUnlikelyToCollide() {
        Set<String> codes = new HashSet<>();
        IntStream.range(0, 1000).forEach(i -> codes.add(generator.generateItemCode()));
        assertThat(codes).hasSize(1000);
    }

    @Test
    void generatedCodesExcludeAmbiguousCharacters() {
        String code = generator.generateItemCode();
        assertThat(code).doesNotContainAnyWhitespaces();
        assertThat(code.replace("ITM-", "")).doesNotContain("0", "O", "1", "I");
    }
}
