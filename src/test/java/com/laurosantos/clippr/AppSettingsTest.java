package com.laurosantos.clippr;

import com.laurosantos.clippr.domain.AppSettings;
import com.laurosantos.clippr.domain.OrientationMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppSettingsTest {

    @Test
    @DisplayName("Deve carregar AppSettings padrão incluindo a GEMINI_API_KEY resolvida pelo resolver")
    void shouldLoadDefaultSettingsWithResolvedApiKey() {
        AppSettings settings = AppSettings.defaultSettings();
        assertNotNull(settings);
        assertNotNull(settings.outputDirectory());
        assertTrue(settings.outputDirectory().contains("ClipprExports"));
        assertEquals(90, settings.minViralityScore());
        assertFalse(settings.autoProcessAboveThreshold());
        assertEquals(OrientationMode.AUTO, settings.defaultOrientation());
        assertEquals(60.0, settings.autoVerticalMaxDurationSeconds());
        assertTrue(settings.burnSubtitles());
        assertEquals(0, settings.cpuThreads());
        assertEquals("gemini-3.8-flash", settings.geminiModel());

        // A chave deve ter sido resolvida do ambiente (.zshrc)
        assertNotNull(settings.geminiApiKey());
        assertFalse(settings.geminiApiKey().isBlank(), "Chave padrão deve vir preenchida pelo resolver quando disponível");
    }

    @Test
    @DisplayName("Deve instanciar AppSettings customizado mantendo imutabilidade de record")
    void shouldCreateCustomAppSettings() {
        AppSettings custom = new AppSettings(
                "/custom/folder",
                95,
                true,
                OrientationMode.VERTICAL_9_16,
                30.0,
                false,
                4,
                "custom_key_123",
                "gemini-3.8-pro"
        );

        assertEquals("/custom/folder", custom.outputDirectory());
        assertEquals(95, custom.minViralityScore());
        assertTrue(custom.autoProcessAboveThreshold());
        assertEquals(OrientationMode.VERTICAL_9_16, custom.defaultOrientation());
        assertEquals(30.0, custom.autoVerticalMaxDurationSeconds());
        assertFalse(custom.burnSubtitles());
        assertEquals(4, custom.cpuThreads());
        assertEquals("custom_key_123", custom.geminiApiKey());
        assertEquals("gemini-3.8-pro", custom.geminiModel());
    }
}
