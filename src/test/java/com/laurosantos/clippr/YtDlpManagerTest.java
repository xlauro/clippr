package com.laurosantos.clippr;

import com.laurosantos.clippr.downloader.YtDlpManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class YtDlpManagerTest {

    @Test
    @DisplayName("Deve localizar ou resolver o binário executável do yt-dlp")
    void shouldResolveYtDlpBinary() {
        YtDlpManager manager = new YtDlpManager();
        Path binary = manager.getBinaryPath();

        assertNotNull(binary, "O caminho do binário não pode ser nulo");
        assertTrue(Files.exists(binary), "O arquivo binário do yt-dlp deve existir");
        assertTrue(Files.isExecutable(binary), "O binário do yt-dlp deve ter permissão de execução");
    }
}
