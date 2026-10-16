package com.laurosantos.clippr;

import com.laurosantos.clippr.ai.GeminiApiKeyResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GeminiApiKeyResolverTest {

    @Test
    @DisplayName("Deve extrair chave definida com export e aspas duplas")
    void shouldExtractKeyWithExportAndDoubleQuotes() {
        String content = """
                # Configurações do shell
                export PATH="$HOME/bin:$PATH"
                export GEMINI_API_KEY="AIzaSyTest_Key_12345"
                export OTHER="foo"
                """;
        String extracted = GeminiApiKeyResolver.parseKeyFromContent(content);
        assertEquals("AIzaSyTest_Key_12345", extracted);
    }

    @Test
    @DisplayName("Deve extrair chave com aspas simples")
    void shouldExtractKeyWithSingleQuotes() {
        String content = "export GEMINI_API_KEY='AIzaSySingleQuotes'";
        String extracted = GeminiApiKeyResolver.parseKeyFromContent(content);
        assertEquals("AIzaSySingleQuotes", extracted);
    }

    @Test
    @DisplayName("Deve extrair chave sem aspas e ignorar comentário na mesma linha")
    void shouldExtractKeyWithoutQuotesAndIgnoreInlineComment() {
        String content = "export GEMINI_API_KEY=AIzaSyNoQuotes # chave do gemini";
        String extracted = GeminiApiKeyResolver.parseKeyFromContent(content);
        assertEquals("AIzaSyNoQuotes", extracted);
    }

    @Test
    @DisplayName("Deve extrair chave sem a palavra-chave export (estilo .env)")
    void shouldExtractKeyWithoutExport() {
        String content = "GEMINI_API_KEY=\"AIzaSyEnvStyle\"";
        String extracted = GeminiApiKeyResolver.parseKeyFromContent(content);
        assertEquals("AIzaSyEnvStyle", extracted);
    }

    @Test
    @DisplayName("Deve ignorar chave comentada e pegar a última definição ativa")
    void shouldIgnoreCommentedLineAndPickLastDefinition() {
        String content = """
                # export GEMINI_API_KEY="chave_antiga_comentada"
                export GEMINI_API_KEY="chave_intermediaria"
                # outro comentario
                export GEMINI_API_KEY="chave_final_valida"
                """;
        String extracted = GeminiApiKeyResolver.parseKeyFromContent(content);
        assertEquals("chave_final_valida", extracted);
    }

    @Test
    @DisplayName("Deve remover ponto-e-vírgula acidental no final")
    void shouldStripAccidentalTrailingSemicolon() {
        String content = "export GEMINI_API_KEY=\"AIzaSyTrailingSemicolon\";";
        String extracted = GeminiApiKeyResolver.parseKeyFromContent(content);
        assertEquals("AIzaSyTrailingSemicolon", extracted);
    }

    @Test
    @DisplayName("Deve ignorar falsos positivos com prefixos ou sufixos na variável")
    void shouldIgnorePrefixAndSuffixVariables() {
        String content = """
                export NOT_GEMINI_API_KEY="errado_1"
                export MY_GEMINI_API_KEY="errado_2"
                export GEMINI_API_KEY_BACKUP="errado_3"
                export GEMINI_API_KEY_OLD="errado_4"
                """;
        String extracted = GeminiApiKeyResolver.parseKeyFromContent(content);
        assertNull(extracted, "Não deve casar com variáveis que possuem prefixo ou sufixo");
    }

    @Test
    @DisplayName("Deve ignorar valores vazios, apenas espaços ou sem valor após o igual")
    void shouldIgnoreEmptyOrWhitespaceValues() {
        String content = """
                export GEMINI_API_KEY=
                export GEMINI_API_KEY=""
                export GEMINI_API_KEY=''
                export GEMINI_API_KEY="   "
                """;
        String extracted = GeminiApiKeyResolver.parseKeyFromContent(content);
        assertNull(extracted, "Valores vazios ou apenas espaços em branco não devem ser considerados chaves válidas");
    }

    @Test
    @DisplayName("Deve ignorar referências a outras variáveis de ambiente ($VAR)")
    void shouldIgnoreVariableReferences() {
        String content = """
                export GEMINI_API_KEY=$OTHER_VAR
                export GEMINI_API_KEY="$MY_SECRET_KEY"
                """;
        String extracted = GeminiApiKeyResolver.parseKeyFromContent(content);
        assertNull(extracted, "Referências a $VAR não devem ser tratadas como valores literais da chave");
    }

    @Test
    @DisplayName("Deve suportar sintaxes alternativas como typeset -x e declare -x e espaçamento flexível")
    void shouldSupportAlternateExportSyntax() {
        String contentTypeset = "typeset -x GEMINI_API_KEY=\"typeset_secret_key\"";
        assertEquals("typeset_secret_key", GeminiApiKeyResolver.parseKeyFromContent(contentTypeset));

        String contentDeclare = "declare -x GEMINI_API_KEY=\"declare_secret_key\"";
        assertEquals("declare_secret_key", GeminiApiKeyResolver.parseKeyFromContent(contentDeclare));

        String contentSpaced = "   export    GEMINI_API_KEY    =    \"spaced_secret_key\"   ";
        assertEquals("spaced_secret_key", GeminiApiKeyResolver.parseKeyFromContent(contentSpaced));
    }

    @Test
    @DisplayName("Deve suportar caracteres especiais usuais em chaves de API (hífens, underscores, pontos)")
    void shouldHandleSpecialCharactersInKey() {
        String complexKey = "AIzaSyD-x9_Qwert12.AbCdEfGh-9988_xyz";
        String content = "export GEMINI_API_KEY=\"" + complexKey + "\"";
        String extracted = GeminiApiKeyResolver.parseKeyFromContent(content);
        assertEquals(complexKey, extracted);
    }

    @Test
    @DisplayName("Deve lidar com arquivos UTF-8 contendo caracteres especiais, acentos e emojis")
    void shouldHandleUtf8AndAccentsInShellConfig(@TempDir Path tempDir) throws IOException {
        Path mockZshrc = tempDir.resolve(".zshrc_utf8");
        String content = """
                # Configurações do usuário Lauro Santos 🚀
                # Opção de acentuação: Produção & Desenvolvimento (não alterar)
                export LC_ALL="pt_BR.UTF-8"
                export GEMINI_API_KEY="AQ.ChaveComEmojiAcentuada_123"
                alias clippr="java -jar clippr.jar"
                """;
        Files.writeString(mockZshrc, content);

        String extracted = GeminiApiKeyResolver.parseKeyFromFile(mockZshrc);
        assertEquals("AQ.ChaveComEmojiAcentuada_123", extracted);
    }

    @Test
    @DisplayName("Deve retornar null graciosamente para caminho nulo, inexistente ou diretório")
    void shouldHandleNonExistentOrDirectoryFile(@TempDir Path tempDir) {
        assertNull(GeminiApiKeyResolver.parseKeyFromFile(null));
        assertNull(GeminiApiKeyResolver.parseKeyFromFile(tempDir.resolve("arquivo_inexistente.zsh")));
        assertNull(GeminiApiKeyResolver.parseKeyFromFile(tempDir)); // Diretório, não arquivo
    }

    @Test
    @DisplayName("Deve processar eficientemente arquivo de configuração extenso (5.000 linhas)")
    void shouldHandleLargeConfigurationFile(@TempDir Path tempDir) throws IOException {
        Path largeFile = tempDir.resolve(".zshrc_large");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2500; i++) {
            sb.append("export PATH_").append(i).append("=\"/usr/local/bin/path_").append(i).append("\"\n");
        }
        sb.append("export GEMINI_API_KEY=\"AQ.ChaveNoMeioDeMilharesDeLinhas\"\n");
        for (int i = 2501; i < 5000; i++) {
            sb.append("alias cmd_").append(i).append("=\"echo hello ").append(i).append("\"\n");
        }
        Files.writeString(largeFile, sb.toString());

        String extracted = GeminiApiKeyResolver.parseKeyFromFile(largeFile);
        assertEquals("AQ.ChaveNoMeioDeMilharesDeLinhas", extracted);
    }

    @Test
    @DisplayName("Deve ler e extrair chave a partir de arquivo em disco")
    void shouldExtractKeyFromFile(@TempDir Path tempDir) throws IOException {
        Path zshrcMock = tempDir.resolve(".zshrc");
        Files.writeString(zshrcMock, "export GEMINI_API_KEY=\"AQ.MockFileKeyFromZshrc\"");

        String extracted = GeminiApiKeyResolver.parseKeyFromFile(zshrcMock);
        assertEquals("AQ.MockFileKeyFromZshrc", extracted);
    }

    @Test
    @DisplayName("Deve detectar com sucesso a GEMINI_API_KEY presente no ambiente real da máquina")
    void shouldDetectKeyFromRealEnvironment() {
        GeminiApiKeyResolver.ResolutionResult result = GeminiApiKeyResolver.resolveWithDetails();
        assertNotNull(result);
        assertTrue(result.isFound(), "Deve encontrar a chave configurada no .zshrc");
        assertFalse(result.key().isBlank(), "A chave não deve ser vazia");
        assertTrue(result.source().contains(".zshrc") || result.source().contains("GEMINI_API_KEY"),
                "Origem deve ser identificada: " + result.source());
    }

    @Test
    @DisplayName("ResolutionResult record deve operar de acordo com o contrato esperado")
    void shouldValidateResolutionResultContract() {
        GeminiApiKeyResolver.ResolutionResult notFound = GeminiApiKeyResolver.ResolutionResult.notFound();
        assertFalse(notFound.isFound());
        assertEquals("", notFound.key());
        assertEquals("Não encontrada", notFound.source());

        GeminiApiKeyResolver.ResolutionResult found = new GeminiApiKeyResolver.ResolutionResult("minha_chave", "~/.zshrc");
        assertTrue(found.isFound());
        assertEquals("minha_chave", found.key());
        assertEquals("~/.zshrc", found.source());

        GeminiApiKeyResolver.ResolutionResult blank = new GeminiApiKeyResolver.ResolutionResult("   ", "~/.zshrc");
        assertFalse(blank.isFound());
    }
}
