package com.laurosantos.clippr.ffmpeg;

import com.laurosantos.clippr.domain.OrientationMode;
import com.laurosantos.clippr.downloader.TranscriptSegment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SubtitleGenerator {

    private SubtitleGenerator() {}

    public static Path generateAssSubtitles(
            List<TranscriptSegment> allSegments,
            double clipStartSeconds,
            double clipEndSeconds,
            Path targetAssFile
    ) throws IOException {
        return generateAssSubtitles(allSegments, clipStartSeconds, clipEndSeconds, OrientationMode.VERTICAL_9_16, targetAssFile);
    }

    public static Path generateAssSubtitles(
            List<TranscriptSegment> allSegments,
            double clipStartSeconds,
            double clipEndSeconds,
            OrientationMode orientation,
            Path targetAssFile
    ) throws IOException {
        OrientationMode mode = orientation != null ? orientation : OrientationMode.VERTICAL_9_16;

        int playResX = (mode == OrientationMode.HORIZONTAL_16_9) ? 1920 : 1080;
        int playResY = (mode == OrientationMode.HORIZONTAL_16_9) ? 1080 : 1920;
        int fontSize = (mode == OrientationMode.HORIZONTAL_16_9) ? 48 : 64;
        int marginV = (mode == OrientationMode.HORIZONTAL_16_9) ? 80 : 280;

        StringBuilder ass = new StringBuilder();

        // Cabeçalho do arquivo ASS
        ass.append("""
                [Script Info]
                ScriptType: v4.00+
                PlayResX: %d
                PlayResY: %d
                WrapStyle: 0
                ScaledBorderAndShadow: yes

                [V4+ Styles]
                Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
                Style: Viral,Arial Black,%d,&H0000FFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,6,2,2,40,40,%d,1

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                """.formatted(playResX, playResY, fontSize, marginV));

        if (allSegments != null) {
            for (TranscriptSegment seg : allSegments) {
                // Filtra apenas as falas dentro do intervalo do corte
                if (seg.endSeconds() >= clipStartSeconds && seg.startSeconds() <= clipEndSeconds) {
                    double relativeStart = Math.max(0, seg.startSeconds() - clipStartSeconds);
                    double relativeEnd = Math.min(clipEndSeconds - clipStartSeconds, seg.endSeconds() - clipStartSeconds);

                    if (relativeEnd > relativeStart) {
                        String startAss = formatAssTime(relativeStart);
                        String endAss = formatAssTime(relativeEnd);
                        String uppercaseText = seg.text().toUpperCase();

                        ass.append("Dialogue: 0,%s,%s,Viral,,0,0,0,,%s\n".formatted(startAss, endAss, uppercaseText));
                    }
                }
            }
        }

        Files.createDirectories(targetAssFile.getParent());
        Files.writeString(targetAssFile, ass.toString());
        return targetAssFile;
    }

    private static String formatAssTime(double seconds) {
        int totalSec = (int) seconds;
        int hours = totalSec / 3600;
        int minutes = (totalSec % 3600) / 60;
        int secs = totalSec % 60;
        int centis = (int) Math.round((seconds - totalSec) * 100);
        if (centis >= 100) {
            secs++;
            centis -= 100;
        }
        return "%d:%02d:%02d.%02d".formatted(hours, minutes, secs, centis);
    }
}
