package com.laurosantos.clippr.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitário para detecção e resolução automática da GEMINI_API_KEY
 * a partir de variáveis de ambiente do sistema e arquivos de configuração shell (.zshrc, .bashrc, .env).
 */
public final class GeminiApiKeyResolver {

    private static final Logger logger = LoggerFactory.getLogger(GeminiApiKeyResolver.class);
    public static final String ENV_VAR_NAME = "GEMINI_API_KEY";

    private static final Pattern KEY_PATTERN = Pattern.compile(
            "^\\s*(?:(?:export|typeset\\s+-x|declare\\s+-x)\\s+)?" + ENV_VAR_NAME + "\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^#\\s]+))"
    );

    public record ResolutionResult(String key, String source) {
        public boolean isFound() {
            return key != null && !key.isBlank();
        }

        public static ResolutionResult notFound() {
            return new ResolutionResult("", "Não encontrada");
        }
    }

    private GeminiApiKeyResolver() {}

    /**
     * Tenta resolver a GEMINI_API_KEY e retorna o resultado detalhado com a origem.
     */
    public static ResolutionResult resolveWithDetails() {
        // 1. Variável de ambiente direta do sistema JVM (System.getenv)
        String envKey = System.getenv(ENV_VAR_NAME);
        if (envKey != null && !envKey.isBlank()) {
            logger.info("GEMINI_API_KEY encontrada em System.getenv");
            return new ResolutionResult(envKey.trim(), "Variável de ambiente (" + ENV_VAR_NAME + ")");
        }

        // 2. Arquivos de configuração de shell (.zshrc, .bashrc, etc.) e .env
        String userHome = System.getProperty("user.home", "");
        String currentDir = System.getProperty("user.dir", ".");

        List<ConfigFileCandidate> candidates = List.of(
                new ConfigFileCandidate(Path.of(userHome, ".zshrc"), "~/.zshrc"),
                new ConfigFileCandidate(Path.of(userHome, ".zshenv"), "~/.zshenv"),
                new ConfigFileCandidate(Path.of(userHome, ".zprofile"), "~/.zprofile"),
                new ConfigFileCandidate(Path.of(userHome, ".bashrc"), "~/.bashrc"),
                new ConfigFileCandidate(Path.of(userHome, ".bash_profile"), "~/.bash_profile"),
                new ConfigFileCandidate(Path.of(userHome, ".profile"), "~/.profile"),
                new ConfigFileCandidate(Path.of(currentDir, ".env"), ".env (projeto)"),
                new ConfigFileCandidate(Path.of(userHome, ".env"), "~/.env")
        );

        for (ConfigFileCandidate candidate : candidates) {
            String key = parseKeyFromFile(candidate.path());
            if (key != null && !key.isBlank()) {
                logger.info("GEMINI_API_KEY encontrada em {}", candidate.description());
                return new ResolutionResult(key, candidate.description());
            }
        }

        // 3. Fallback: execução de subprocesso zsh/bash caso a variável dependa de eval ou sourcing encadeado
        String zshKey = resolveFromShell("zsh", "-c", "source ~/.zshrc 2>/dev/null; echo -n \"$" + ENV_VAR_NAME + "\"");
        if (zshKey != null && !zshKey.isBlank()) {
            logger.info("GEMINI_API_KEY encontrada via shell zsh");
            return new ResolutionResult(zshKey, "Shell zsh (~/.zshrc)");
        }

        String bashKey = resolveFromShell("bash", "-c", "source ~/.bashrc 2>/dev/null; echo -n \"$" + ENV_VAR_NAME + "\"");
        if (bashKey != null && !bashKey.isBlank()) {
            logger.info("GEMINI_API_KEY encontrada via shell bash");
            return new ResolutionResult(bashKey, "Shell bash (~/.bashrc)");
        }

        logger.warn("GEMINI_API_KEY não foi encontrada nas variáveis de ambiente ou arquivos de shell.");
        return ResolutionResult.notFound();
    }

    /**
     * Retorna a chave resolvida ou string vazia caso não seja encontrada.
     */
    public static String resolveApiKey() {
        return resolveWithDetails().key();
    }

    /**
     * Lê e extrai a chave de um arquivo de configuração.
     */
    public static String parseKeyFromFile(Path path) {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            return null;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return extractFromReader(reader);
        } catch (Exception e) {
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.ISO_8859_1)) {
                return extractFromReader(reader);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    /**
     * Extrai a chave a partir de uma string de conteúdo (útil para testes unitários).
     */
    public static String parseKeyFromContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String lastFound = null;
        for (String line : content.split("\\R")) {
            String candidate = extractFromLine(line);
            if (candidate != null) {
                lastFound = candidate;
            }
        }
        return lastFound;
    }

    private static String extractFromReader(BufferedReader reader) throws Exception {
        String line;
        String lastFound = null;
        while ((line = reader.readLine()) != null) {
            String candidate = extractFromLine(line);
            if (candidate != null) {
                lastFound = candidate;
            }
        }
        return lastFound;
    }

    private static String extractFromLine(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("#") || !trimmed.contains(ENV_VAR_NAME)) {
            return null;
        }

        Matcher matcher = KEY_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            String val = matcher.group(1);
            if (val == null) val = matcher.group(2);
            if (val == null) val = matcher.group(3);
            if (val != null) {
                val = val.replaceAll(";+$", "").trim();
                // Se a variável estiver referenciando outra variável ($VAR), não considerar valor literal válido
                if (!val.isBlank() && !val.startsWith("$")) {
                    return val;
                }
            }
        }
        return null;
    }

    private static String resolveFromShell(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String out = reader.readLine();
                    if (out != null && !out.isBlank()) {
                        return out.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private record ConfigFileCandidate(Path path, String description) {}
}
