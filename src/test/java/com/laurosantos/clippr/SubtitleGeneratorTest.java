package com.laurosantos.clippr;

import com.laurosantos.clippr.downloader.TranscriptSegment;
import com.laurosantos.clippr.ffmpeg.SubtitleGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubtitleGeneratorTest {

    @Test
    @DisplayName("Deve gerar arquivo de legenda .ass estilizado com timestamps relativos ao início do corte")
    void shouldGenerateAssSubtitlesCorrectly(@TempDir Path tempDir) throws IOException {
        List<TranscriptSegment> segments = List.of(
                new TranscriptSegment(5.0, 10.0, "trecho antes do corte"),
                new TranscriptSegment(10.0, 20.0, "gancho do corte viral"),
                new TranscriptSegment(20.0, 30.0, "desfecho da história"),
                new TranscriptSegment(35.0, 40.0, "trecho após o corte")
        );

        Path targetAss = tempDir.resolve("teste.ass");
        // Corte começa em 10.0s e termina em 30.0s
        SubtitleGenerator.generateAssSubtitles(segments, 10.0, 30.0, targetAss);

        assertTrue(Files.exists(targetAss));
        String content = Files.readString(targetAss);

        assertTrue(content.contains("[Script Info]"));
        assertTrue(content.contains("Style: Viral"));
        // Deve conter o primeiro segmento normalizado para 00:00:00.00
        assertTrue(content.contains("GANCHO DO CORTE VIRAL"));
        assertTrue(content.contains("DESFECHO DA HISTÓRIA"));
        assertFalse(content.contains("TRECHO ANTES DO CORTE"));
        assertFalse(content.contains("TRECHO APÓS O CORTE"));
    }
}
