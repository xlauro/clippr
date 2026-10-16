package com.laurosantos.clippr;

import com.laurosantos.clippr.ai.PromptFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptFactoryTest {

    @Test
    @DisplayName("SYSTEM_INSTRUCTION deve conter instruções detalhadas para thumbnail de cortes longos e formato JSON")
    void systemInstructionShouldContainThumbnailGuidelines() {
        String instruction = PromptFactory.SYSTEM_INSTRUCTION;

        assertNotNull(instruction);
        assertTrue(instruction.contains("thumbnailTimestamp"), "Deve orientar sobre thumbnailTimestamp");
        assertTrue(instruction.contains("thumbnailHeadline"), "Deve orientar sobre thumbnailHeadline");
        assertTrue(instruction.contains("CORTES LONGOS"), "Deve orientar sobre cortes longos");
        assertTrue(instruction.contains("CORTES CURTOS"), "Deve orientar sobre cortes curtos");
        assertTrue(instruction.contains("O SEGREDO REVELADO"), "Deve conter exemplo de headline de capa");
        assertTrue(instruction.contains("\"thumbnailTimestamp\": 25.4"), "Deve conter exemplo no schema JSON");
    }

    @Test
    @DisplayName("buildUserPrompt deve injetar metadados do vídeo e instruções para thumbnail de capa")
    void buildUserPromptShouldIncludeMetadataAndThumbnailInstructions() {
        String title = "Como Escalar Seu Negócio em 2026";
        String author = "Mestre dos Negócios";
        String transcript = "[00:10.000 --> 00:45.000] O grande segredo da escala...";

        String prompt = PromptFactory.buildUserPrompt(title, author, transcript);

        assertNotNull(prompt);
        assertTrue(prompt.contains(title));
        assertTrue(prompt.contains(author));
        assertTrue(prompt.contains(transcript));
        assertTrue(prompt.contains("thumbnailTimestamp"));
        assertTrue(prompt.contains("thumbnailHeadline"));
        assertTrue(prompt.contains("CORTES LONGOS"));
    }
}
