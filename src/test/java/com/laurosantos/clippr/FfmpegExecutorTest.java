package com.laurosantos.clippr;

import com.laurosantos.clippr.ffmpeg.FfmpegExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegExecutorTest {

    @Test
    @DisplayName("Deve localizar o binário do FFmpeg no sistema")
    void shouldResolveFfmpegBinary() {
        FfmpegExecutor executor = new FfmpegExecutor();
        String binary = executor.getFfmpegBinary();

        assertNotNull(binary, "O caminho do FFmpeg não pode ser nulo");
        assertTrue(binary.contains("ffmpeg"), "O comando deve apontar para o ffmpeg");
    }
}
