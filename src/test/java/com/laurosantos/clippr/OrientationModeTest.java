package com.laurosantos.clippr;

import com.laurosantos.clippr.domain.OrientationMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrientationModeTest {

    @Test
    @DisplayName("Modo AUTO deve escolher Vertical (9:16) se duração for menor ou igual ao limiar de 60s")
    void shouldResolveVerticalWhenUnderThreshold() {
        OrientationMode mode = OrientationMode.resolveForDuration(OrientationMode.AUTO, 45.0, 60.0);
        assertEquals(OrientationMode.VERTICAL_9_16, mode);

        OrientationMode boundaryMode = OrientationMode.resolveForDuration(OrientationMode.AUTO, 60.0, 60.0);
        assertEquals(OrientationMode.VERTICAL_9_16, boundaryMode);
    }

    @Test
    @DisplayName("Modo AUTO deve escolher Horizontal (16:9) se duração for superior ao limiar de 60s")
    void shouldResolveHorizontalWhenOverThreshold() {
        OrientationMode mode = OrientationMode.resolveForDuration(OrientationMode.AUTO, 60.1, 60.0);
        assertEquals(OrientationMode.HORIZONTAL_16_9, mode);

        OrientationMode longMode = OrientationMode.resolveForDuration(OrientationMode.AUTO, 180.0, 60.0);
        assertEquals(OrientationMode.HORIZONTAL_16_9, longMode);
    }

    @Test
    @DisplayName("Modos forçados (VERTICAL ou HORIZONTAL) não devem ser alterados pela duração")
    void shouldRespectForcedOrientation() {
        assertEquals(OrientationMode.VERTICAL_9_16,
                OrientationMode.resolveForDuration(OrientationMode.VERTICAL_9_16, 300.0, 60.0));

        assertEquals(OrientationMode.HORIZONTAL_16_9,
                OrientationMode.resolveForDuration(OrientationMode.HORIZONTAL_16_9, 15.0, 60.0));
    }
}
