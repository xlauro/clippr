package com.laurosantos.clippr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppTest {

    @Test
    @DisplayName("Garante que a classe principal App existe e pode ser referenciada")
    void testAppClassReference() {
        assertNotNull(App.class);
    }
}
