package com.bms.backend.global.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonotonicUlidGeneratorTests {

    @Test
    void createsUniqueLexicallyOrderedUlids() {
        MonotonicUlidGenerator generator = new MonotonicUlidGenerator();
        List<String> generated = new ArrayList<>();

        for (int count = 0; count < 1_000; count++) {
            generated.add(generator.next());
        }

        assertThat(new HashSet<>(generated)).hasSize(1_000);
        assertThat(generated).allMatch(value -> value.matches("[0-9A-HJKMNP-TV-Z]{26}"));
        assertThat(generated).isSorted();
    }
}
