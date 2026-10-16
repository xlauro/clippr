package com.laurosantos.clippr;

import com.laurosantos.clippr.ffmpeg.ThumbnailGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThumbnailGeneratorTest {

    @Test
    @DisplayName("Deve compor thumbnail 16:9 (1280x720) a partir de uma imagem base com estética YouTube Viral")
    void shouldComposeThumbnailWithViralAesthetic() {
        ThumbnailGenerator generator = new ThumbnailGenerator();

        // Imagem simulada (ex: frame de vídeo 1920x1080)
        BufferedImage baseFrame = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = baseFrame.createGraphics();
        g2.setColor(new Color(40, 70, 120)); // Fundo azul simulando cena de vídeo
        g2.fillRect(0, 0, 1920, 1080);
        g2.dispose();

        String headline = "O SEGREDO REVELADO";
        BufferedImage result = generator.composeThumbnail(baseFrame, headline);

        assertNotNull(result);
        assertEquals(ThumbnailGenerator.TARGET_WIDTH, result.getWidth());
        assertEquals(ThumbnailGenerator.TARGET_HEIGHT, result.getHeight());

        // Verifica que o canto inferior direito não contém elementos opacos de texto (margem de segurança)
        // YouTube duration badge fica aproximadamente em x: 1100 a 1260, y: 650 a 700
        int badgeAreaPixel = result.getRGB(1150, 670);
        Color badgeColor = new Color(badgeAreaPixel, true);
        // Não deve ser a cor amarela vibrante nem branca de texto
        assertNotEquals(ThumbnailGenerator.COLOR_CTR_YELLOW.getRGB(), badgeAreaPixel);
        assertNotEquals(Color.WHITE.getRGB(), badgeAreaPixel);
    }

    @Test
    @DisplayName("Deve quebrar linhas automaticamente (word wrap) com limite de 2-3 palavras por linha")
    void shouldWrapHeadlineCorrectly() {
        Font font = ThumbnailGenerator.selectHeadlineFont(60);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        int maxWidth = (int) (ThumbnailGenerator.TARGET_WIDTH * 0.52);

        // 1. Título curto (2 palavras) -> 1 linha
        List<String> shortLines = ThumbnailGenerator.wrapHeadline("CORTE ÉPICO", font, frc, maxWidth);
        assertEquals(1, shortLines.size());
        assertEquals("CORTE ÉPICO", shortLines.getFirst());

        // 2. Título médio (4 palavras) -> Quebra em pelo menos 2 linhas
        List<String> mediumLines = ThumbnailGenerator.wrapHeadline("COMO VIRALIZAR NO YOUTUBE", font, frc, maxWidth);
        assertTrue(mediumLines.size() >= 2, "Deveria quebrar em pelo menos 2 linhas");
        assertTrue(mediumLines.stream().allMatch(l -> l.split("\\s+").length <= 3), "Cada linha deve conter no máximo 3 palavras");

        // 3. Título muito longo (> 12 palavras) -> Limita a 4 linhas com reticências
        String longTitle = "COMO GANHAR DINHEIRO NA INTERNET UTILIZANDO INTELIGENCIA ARTIFICIAL E FERRAMENTAS MODERNAS DE EDICAO EM 2026";
        List<String> longLines = ThumbnailGenerator.wrapHeadline(longTitle, font, frc, maxWidth);
        assertTrue(longLines.size() <= 4, "Não deve ultrapassar 4 linhas para preservar layout");
        assertTrue(longLines.getLast().endsWith("..."), "Última linha deve conter reticências");
    }

    @Test
    @DisplayName("Deve salvar arquivo JPEG com alta qualidade no disco e permitir recarregamento")
    void shouldSaveJpegWithHighQuality(@TempDir Path tempDir) throws IOException {
        BufferedImage image = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, 0, 1280, 720);
        g.setColor(ThumbnailGenerator.COLOR_CTR_YELLOW);
        g.fillOval(100, 100, 200, 200);
        g.dispose();

        Path outputPath = tempDir.resolve("test_thumb.jpg");
        ThumbnailGenerator.saveJpegWithQuality(image, outputPath, 0.95f);

        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);

        BufferedImage reloaded = ImageIO.read(outputPath.toFile());
        assertNotNull(reloaded);
        assertEquals(1280, reloaded.getWidth());
        assertEquals(720, reloaded.getHeight());
    }

    @Test
    @DisplayName("Deve tratar headline nula ou vazia sem lançar exceção")
    void shouldHandleNullOrBlankHeadlineGracefully() {
        ThumbnailGenerator generator = new ThumbnailGenerator();
        BufferedImage baseFrame = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);

        assertDoesNotThrow(() -> {
            BufferedImage res1 = generator.composeThumbnail(baseFrame, null);
            assertNotNull(res1);
            assertEquals(ThumbnailGenerator.TARGET_WIDTH, res1.getWidth());
            assertEquals(ThumbnailGenerator.TARGET_HEIGHT, res1.getHeight());

            BufferedImage res2 = generator.composeThumbnail(baseFrame, "   ");
            assertNotNull(res2);
        });
    }

    @Test
    @DisplayName("Deve lançar IllegalArgumentException se o arquivo de vídeo não existir no generateThumbnail")
    void shouldThrowWhenVideoDoesNotExist(@TempDir Path tempDir) {
        ThumbnailGenerator generator = new ThumbnailGenerator();
        Path nonExistent = tempDir.resolve("missing_video.mp4");

        assertThrows(IllegalArgumentException.class, () ->
                generator.generateThumbnail(nonExistent, 10.0, "HEADLINE", tempDir, "clip_test")
        );
    }
}
