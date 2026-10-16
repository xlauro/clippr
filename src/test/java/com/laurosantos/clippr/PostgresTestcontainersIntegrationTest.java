package com.laurosantos.clippr;

import com.laurosantos.clippr.domain.AppSettings;
import com.laurosantos.clippr.domain.ClipCandidate;
import com.laurosantos.clippr.domain.OrientationMode;
import com.laurosantos.clippr.domain.ProcessingStatus;
import com.laurosantos.clippr.domain.VideoSource;
import com.laurosantos.clippr.persistence.DatabaseManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class PostgresTestcontainersIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("clippr_testcontainers_db")
            .withUsername("test_user")
            .withPassword("test_secret");

    private static DatabaseManager db;

    @BeforeAll
    static void setUp() {
        postgres.start();
        System.setProperty("clippr.db.url", postgres.getJdbcUrl());
        System.setProperty("clippr.db.user", postgres.getUsername());
        System.setProperty("clippr.db.password", postgres.getPassword());

        db = DatabaseManager.getInstance();
        db.close(); // Garante reset para nova URL
        boolean initialized = db.init();
        assertTrue(initialized, "DatabaseManager deve inicializar com o container PostgreSQL do Testcontainers");
    }

    @AfterAll
    static void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    @DisplayName("Testcontainers: Deve persistir e recuperar vídeo completo com múltiplos estágios de status")
    void shouldPersistAndTransitionVideoStatus() {
        assertTrue(db.isConnected(), "Banco de dados do container deve estar conectado");

        // Inserção inicial
        VideoSource video = VideoSource.fromUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        VideoSource saved = db.saveVideo(video);
        assertNotNull(saved.id(), "O ID do vídeo deve ser gerado pelo PostgreSQL");
        assertEquals("dQw4w9WgXcQ", saved.videoId());
        assertEquals(ProcessingStatus.PENDING, saved.status());

        // Transição 1: FETCHING_TRANSCRIPT
        db.updateVideoStatus(saved.id(), ProcessingStatus.FETCHING_TRANSCRIPT, null);

        // Transição 2: ANALYZING_AI
        db.updateVideoStatus(saved.id(), ProcessingStatus.ANALYZING_AI, null);

        // Transição 3: COMPLETED
        db.updateVideoStatus(saved.id(), ProcessingStatus.COMPLETED, null);
    }

    @Test
    @DisplayName("Testcontainers: Deve persistir múltiplos clipes virais com metadados e scores diferentes")
    void shouldPersistMultipleClipsWithVaryingScores() {
        VideoSource video = VideoSource.fromUrl("https://www.youtube.com/watch?v=viral_test_123");
        VideoSource savedVideo = db.saveVideo(video);

        ClipCandidate viralClip = new ClipCandidate(
                null,
                savedVideo.id(),
                "Revelação Inédita",
                "Você nunca mais verá a IA da mesma forma",
                12.0,
                55.0,
                97,
                "História de altíssimo impacto emocional com gancho forte",
                "Legenda gerada para TikTok #viral #ia",
                List.of("viral", "shorts", "ai"),
                OrientationMode.VERTICAL_9_16,
                true,
                "/exports/clip_01.mp4",
                "/exports/clip_01.json",
                ProcessingStatus.PENDING_APPROVAL
        );

        ClipCandidate lowClip = new ClipCandidate(
                null,
                savedVideo.id(),
                "Trecho Comum",
                "Introdução ao assunto",
                60.0,
                110.0,
                65,
                "Trecho sem gancho chamativo",
                "Introdução",
                List.of("intro"),
                OrientationMode.HORIZONTAL_16_9,
                false,
                null,
                null,
                ProcessingStatus.DISCARDED_LOW_SCORE
        );

        List<ClipCandidate> savedClips = db.saveClips(savedVideo.id(), List.of(viralClip, lowClip));
        assertEquals(2, savedClips.size());

        ClipCandidate c1 = savedClips.get(0);
        assertNotNull(c1.id());
        assertEquals(97, c1.viralityScore());
        assertTrue(c1.isViralWorthy(90));
        assertEquals(43.0, c1.durationSeconds(), 0.001);

        ClipCandidate c2 = savedClips.get(1);
        assertNotNull(c2.id());
        assertEquals(65, c2.viralityScore());
        assertFalse(c2.isViralWorthy(90));

        // Teste de Ajuste Fino dos Marcadores de Tempo
        db.updateClipTimeBounds(c1.id(), 11.5, 56.2);
    }

    @Test
    @DisplayName("Testcontainers: Deve persistir e atualizar configurações da aplicação")
    void shouldPersistAndRetrieveAppSettings() {
        AppSettings customSettings = new AppSettings(
                "/custom/export/path",
                92,
                true,
                OrientationMode.VERTICAL_9_16,
                45.0,
                true,
                8,
                "custom-testcontainers-api-key",
                "gemini-3.8-flash"
        );

        db.saveSettings(customSettings);
        AppSettings loaded = db.getSettings();

        assertNotNull(loaded);
        assertEquals("/custom/export/path", loaded.outputDirectory());
        assertEquals(92, loaded.minViralityScore());
        assertTrue(loaded.autoProcessAboveThreshold());
        assertEquals(OrientationMode.VERTICAL_9_16, loaded.defaultOrientation());
        assertEquals(8, loaded.cpuThreads());
        assertEquals("custom-testcontainers-api-key", loaded.geminiApiKey());
    }

    @Test
    @DisplayName("Deve auto-preencher geminiApiKey a partir do resolver se salvo com chave vazia")
    void shouldAutoResolveGeminiApiKeyWhenEmptyInDatabase() {
        AppSettings emptyKeySettings = new AppSettings(
                "/test/export",
                85,
                false,
                OrientationMode.AUTO,
                60.0,
                true,
                4,
                "", // Chave vazia no banco
                "gemini-3.8-flash"
        );

        db.saveSettings(emptyKeySettings);
        AppSettings loaded = db.getSettings();

        assertNotNull(loaded);
        // O resolver deve ter detectado a chave real do ambiente (.zshrc) e atualizado
        assertFalse(loaded.geminiApiKey().isBlank(), "A chave deve ser resolvida automaticamente quando vazia");
        assertTrue(loaded.geminiApiKey().startsWith("AQ."), "A chave resolvida deve corresponder à chave do ambiente");
    }
}
