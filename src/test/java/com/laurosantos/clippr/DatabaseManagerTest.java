package com.laurosantos.clippr;

import com.laurosantos.clippr.domain.*;
import com.laurosantos.clippr.persistence.DatabaseManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @Test
    @DisplayName("Deve conectar ao PostgreSQL e persistir vídeo e clipes com sucesso")
    void shouldPersistAndRetrieveVideoAndClips() {
        DatabaseManager db = DatabaseManager.getInstance();
        boolean connected = db.init();

        if (!connected) {
            System.out.println("Container PostgreSQL não acessível durante teste, pulando teste de persistência.");
            return;
        }

        assertTrue(db.isConnected(), "O banco de dados deve reportar conexão ativa");

        // 1. Salvar vídeo
        VideoSource video = VideoSource.fromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        VideoSource savedVideo = db.saveVideo(video);

        assertNotNull(savedVideo.id(), "O ID do vídeo persistido não deve ser nulo");
        assertEquals("dQw4w9WgXcQ", savedVideo.videoId());

        // 2. Atualizar status
        db.updateVideoStatus(savedVideo.id(), ProcessingStatus.ANALYZING_AI, null);

        // 3. Salvar clipes
        ClipCandidate clip = new ClipCandidate(
                null,
                savedVideo.id(),
                "Corte Épico",
                "O momento que mudou tudo...",
                15.0,
                45.0,
                95,
                "Excelente história com início, meio e fim.",
                "Legenda sugerida para redes sociais",
                List.of("viral", "shorts", "clippr"),
                OrientationMode.VERTICAL_9_16,
                true,
                "/tmp/corte.mp4",
                "/tmp/corte.json",
                ProcessingStatus.PENDING_APPROVAL
        );

        List<ClipCandidate> savedClips = db.saveClips(savedVideo.id(), List.of(clip));
        assertEquals(1, savedClips.size());
        assertNotNull(savedClips.getFirst().id());
        assertEquals(95, savedClips.getFirst().viralityScore());

        // 4. Testar configurações
        AppSettings settings = db.getSettings();
        assertNotNull(settings);
        assertEquals(90, settings.minViralityScore());
    }
}
