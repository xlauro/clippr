package com.laurosantos.clippr.ffmpeg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Gerador de thumbnails de alto impacto para cortes longos e de destaque (formato YouTube Viral 16:9).
 * Executa extração cirúrgica de frames limpos via FFmpeg e composição gráfica profissional via Java2D.
 */
public class ThumbnailGenerator {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailGenerator.class);

    public static final int TARGET_WIDTH = 1280;
    public static final int TARGET_HEIGHT = 720;

    public static final Color COLOR_CTR_YELLOW = new Color(255, 230, 0); // #FFE600
    public static final Color COLOR_WHITE = Color.WHITE;
    public static final Color SHADOW_COLOR = new Color(0, 0, 0, 210);

    private final String ffmpegBinary;

    public record ThumbnailResult(Path finalThumbnailPath, Path rawFramePath) {}

    public ThumbnailGenerator() {
        this(resolveFfmpegBinary());
    }

    public ThumbnailGenerator(String ffmpegBinary) {
        this.ffmpegBinary = ffmpegBinary != null && !ffmpegBinary.isBlank() ? ffmpegBinary : resolveFfmpegBinary();
    }

    public String getFfmpegBinary() {
        return ffmpegBinary;
    }

    /**
     * Gera a thumbnail completa: extrai o frame limpo e compõe a capa viral com headline.
     *
     * @param videoFile Arquivo de vídeo de origem.
     * @param timestampSeconds Timestamp no vídeo para extrair o frame (em segundos).
     * @param headline Texto de chamada (headline/hook) para estampar na capa.
     * @param outputDirectory Diretório de destino.
     * @param baseFileName Nome base para os arquivos de saída.
     * @return ThumbnailResult com caminhos da imagem final e do frame limpo.
     * @throws Exception Caso ocorra falha na extração FFmpeg ou no processamento gráfico.
     */
    public ThumbnailResult generateThumbnail(
            Path videoFile,
            double timestampSeconds,
            String headline,
            Path outputDirectory,
            String baseFileName
    ) throws Exception {
        if (!Files.exists(videoFile)) {
            throw new IllegalArgumentException("Arquivo de vídeo não encontrado: " + videoFile);
        }
        Files.createDirectories(outputDirectory);

        Path rawFramePath = outputDirectory.resolve(baseFileName + "_raw.jpg");
        Path finalThumbnailPath = outputDirectory.resolve(baseFileName + "_thumb.jpg");

        // 1. Extração do frame limpo via FFmpeg
        extractCleanFrame(videoFile, timestampSeconds, rawFramePath);

        // 2. Composição visual Java2D
        BufferedImage rawImage = ImageIO.read(rawFramePath.toFile());
        if (rawImage == null) {
            throw new IOException("Falha ao carregar imagem extraída via ImageIO: " + rawFramePath);
        }

        BufferedImage composedImage = composeThumbnail(rawImage, headline);
        saveJpegWithQuality(composedImage, finalThumbnailPath, 0.95f);

        logger.info("Thumbnail gerada com sucesso: {}", finalThumbnailPath.toAbsolutePath());
        return new ThumbnailResult(finalThumbnailPath, rawFramePath);
    }

    /**
     * Extrai frame cirúrgico em timestamp exato via FFmpeg:
     * ffmpeg -y -ss <timestampSeconds> -i <sourceVideoFile> -vframes 1 -q:v 2 <rawFrameOutputPath>
     */
    public void extractCleanFrame(Path videoFile, double timestampSeconds, Path rawFrameOutputPath) throws Exception {
        if (rawFrameOutputPath.getParent() != null) {
            Files.createDirectories(rawFrameOutputPath.getParent());
        }

        double safeTimestamp = Math.max(0.0, timestampSeconds);
        List<String> command = List.of(
                ffmpegBinary,
                "-y",
                "-ss", String.format(Locale.ROOT, "%.3f", safeTimestamp),
                "-i", videoFile.toAbsolutePath().toString(),
                "-vframes", "1",
                "-q:v", "2",
                rawFrameOutputPath.toAbsolutePath().toString()
        );

        logger.info("Executando extração de frame FFmpeg: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.trace("[FFmpeg Thumb Frame] {}", line);
            }
        }

        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            throw new RuntimeException("Tempo limite excedido na extração do frame via FFmpeg");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0 || !Files.exists(rawFrameOutputPath)) {
            throw new RuntimeException("Falha na extração de frame FFmpeg (exit code: " + exitCode + ")");
        }
    }

    /**
     * Renderização e composição visual via Java2D (Graphics2D):
     * - Garante proporção YouTube 16:9 (1280x720) com ajuste inteligente (crop centralizado se necessário).
     * - Aplica gradiente escuro na área do texto para contraste infinito.
     * - Tipografia de impacto bold, contorno preto espesso, sombra projetada e cores vibrantes (#FFE600 e #FFFFFF).
     * - Quebra de linha automática (word wrap) e respeito à margem de segurança do YouTube.
     */
    public BufferedImage composeThumbnail(BufferedImage rawImage, String headline) {
        BufferedImage canvas = new BufferedImage(TARGET_WIDTH, TARGET_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = canvas.createGraphics();

        try {
            // Configurações de máxima fidelidade visual e anti-aliasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // 1. Escala proporcional com corte centralizado (cover) para garantir 16:9
            double scale = Math.max((double) TARGET_WIDTH / rawImage.getWidth(), (double) TARGET_HEIGHT / rawImage.getHeight());
            int scaledW = (int) Math.round(rawImage.getWidth() * scale);
            int scaledH = (int) Math.round(rawImage.getHeight() * scale);
            int offsetX = (TARGET_WIDTH - scaledW) / 2;
            int offsetY = (TARGET_HEIGHT - scaledH) / 2;

            g2d.drawImage(rawImage, offsetX, offsetY, scaledW, scaledH, null);

            // 2. Gradiente escuro lateral (vinheta cinematográfica para contraste no terço esquerdo/central)
            Paint horizontalGradient = new GradientPaint(
                    0, 0, new Color(0, 0, 0, 220),
                    (int) (TARGET_WIDTH * 0.65), 0, new Color(0, 0, 0, 0)
            );
            g2d.setPaint(horizontalGradient);
            g2d.fillRect(0, 0, TARGET_WIDTH, TARGET_HEIGHT);

            Paint verticalVignette = new GradientPaint(
                    0, 0, new Color(0, 0, 0, 110),
                    0, (int) (TARGET_HEIGHT * 0.35), new Color(0, 0, 0, 0)
            );
            g2d.setPaint(verticalVignette);
            g2d.fillRect(0, 0, TARGET_WIDTH, TARGET_HEIGHT);

            // 3. Tipografia YouTube Viral e posicionamento com margens de segurança
            if (headline != null && !headline.isBlank()) {
                drawViralHeadline(g2d, headline.trim());
            }

            return canvas;
        } finally {
            g2d.dispose();
        }
    }

    private void drawViralHeadline(Graphics2D g2d, String rawHeadline) {
        String upperText = rawHeadline.toUpperCase(Locale.ROOT);
        int maxTextWidth = (int) (TARGET_WIDTH * 0.52); // Reserva 48% à direita para rosto/ação
        int maxTotalHeight = (int) (TARGET_HEIGHT * 0.55); // Terço superior/médio

        int baseFontSize = 66;
        int minFontSize = 38;
        Font font = null;
        List<String> lines = null;
        FontRenderContext frc = g2d.getFontRenderContext();
        int lineHeight = 0;
        int totalTextHeight = 0;

        for (int size = baseFontSize; size >= minFontSize; size -= 4) {
            font = selectHeadlineFont(size);
            lines = wrapHeadline(upperText, font, frc, maxTextWidth);
            FontMetrics fm = g2d.getFontMetrics(font);
            lineHeight = (int) (fm.getAscent() * 1.18);
            totalTextHeight = lines.size() * lineHeight;

            if (totalTextHeight <= maxTotalHeight || size == minFontSize) {
                break;
            }
        }

        if (lines == null || lines.isEmpty() || font == null) {
            return;
        }

        FontMetrics fm = g2d.getFontMetrics(font);
        int startX = 65; // Margem de segurança esquerda
        // Centraliza verticalmente no terço superior/médio (longe do rodapé e canto inferior direito)
        int startY = 110 + Math.max(0, (maxTotalHeight - totalTextHeight) / 3);

        float strokeWidth = Math.max(6.5f, font.getSize() / 7.5f);
        int shadowOffset = Math.max(4, font.getSize() / 12);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int baselineY = startY + (i * lineHeight) + fm.getAscent();

            // Cor de alto CTR: Alternância Branco / Amarelo Vibrante
            Color textColor;
            if (lines.size() == 1) {
                textColor = COLOR_CTR_YELLOW;
            } else {
                textColor = (i % 2 == 0) ? COLOR_WHITE : COLOR_CTR_YELLOW;
            }

            GlyphVector gv = font.createGlyphVector(frc, line);
            Shape outline = gv.getOutline(startX, baselineY);

            // Sombra projetada (drop shadow)
            AffineTransform shadowTransform = AffineTransform.getTranslateInstance(shadowOffset, shadowOffset + 2);
            Shape shadowShape = shadowTransform.createTransformedShape(outline);
            g2d.setColor(SHADOW_COLOR);
            g2d.fill(shadowShape);

            // Contorno preto espesso (stroke)
            g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(Color.BLACK);
            g2d.draw(outline);

            // Preenchimento com cor de alto impacto
            g2d.setColor(textColor);
            g2d.fill(outline);
        }
    }

    /**
     * Quebra de linha automática (word wrap) com limite de 2 a 3 palavras por linha para impacto máximo.
     */
    public static List<String> wrapHeadline(String headline, Font font, FontRenderContext frc, int maxWidth) {
        if (headline == null || headline.isBlank()) {
            return List.of();
        }

        String[] words = headline.trim().split("\\s+");
        if (words.length == 0) {
            return List.of();
        }

        if (words.length <= 3) {
            String singleLine = String.join(" ", words);
            double width = font.getStringBounds(singleLine, frc).getWidth();
            if (width <= maxWidth) {
                return List.of(singleLine);
            }
        }

        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int wordsInCurrentLine = 0;

        for (String word : words) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            double candidateWidth = font.getStringBounds(candidate, frc).getWidth();

            if (!currentLine.isEmpty() && (candidateWidth > maxWidth || wordsInCurrentLine >= 3)) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
                wordsInCurrentLine = 1;
            } else {
                currentLine = new StringBuilder(candidate);
                wordsInCurrentLine++;
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        if (lines.size() > 4) {
            List<String> capped = new ArrayList<>(lines.subList(0, 3));
            capped.add(lines.get(3) + "...");
            return capped;
        }

        return lines;
    }

    /**
     * Seleciona tipografia sem serifa de alto impacto disponível no ambiente gráfico.
     */
    public static Font selectHeadlineFont(int fontSize) {
        String[] fontCandidates = {"Impact", "Arial Black", "Montserrat", "DejaVu Sans", "Liberation Sans", Font.SANS_SERIF};
        Set<String> available = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());

        for (String candidate : fontCandidates) {
            if (available.contains(candidate)) {
                return new Font(candidate, Font.BOLD, fontSize);
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
    }

    /**
     * Salva a imagem em JPEG com qualidade especificada (0.0 a 1.0).
     */
    public static void saveJpegWithQuality(BufferedImage image, Path outputPath, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (writers.hasNext()) {
            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputPath.toFile())) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(Math.clamp(quality, 0.0f, 1.0f));
                }
                writer.write(null, new IIOImage(image, null, null), param);
                return;
            } finally {
                writer.dispose();
            }
        }
        ImageIO.write(image, "jpg", outputPath.toFile());
    }

    private static String resolveFfmpegBinary() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                Path candidate = Path.of(dir, "ffmpeg");
                if (Files.isExecutable(candidate)) {
                    return candidate.toString();
                }
            }
        }
        if (Files.isExecutable(Path.of("/usr/bin/ffmpeg"))) {
            return "/usr/bin/ffmpeg";
        }
        return "ffmpeg";
    }
}
