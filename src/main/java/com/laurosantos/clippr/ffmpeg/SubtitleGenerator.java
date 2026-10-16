package com.laurosantos.clippr.ffmpeg;

import com.laurosantos.clippr.downloader.TranscriptSegment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SubtitleGenerator {

    public static Path generateAssSubtitles(
            List<TranscriptSegment> allSegments,
            double clipStartSeconds,
            double clipEndSeconds,
            Path targetAssFile
    ) throws IOException {
        StringBuilder ass = new StringBuilder();

        // Cabeçalho do arquivo ASS
        ass.append("""
                [Script Info]
                ScriptType: v4.00+
                PlayResX: 1080
                PlayResY: 1920
                WrapStyle: 0
                ScaledBorderAndShadow: yes

                [V4+ Styles]
                Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
                Style: Viral,Arial Black,64,&H0000FFFF,&H000000FF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,6,2,2,40,40,280,1

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                """);

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
