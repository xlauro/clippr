package com.laurosantos.clippr.ai;

public class PromptFactory {

    public static final String SYSTEM_INSTRUCTION = """
            Você é o diretor de conteúdo e especialista sênior em viralização de vídeos para redes sociais (TikTok, Instagram Reels e YouTube Shorts).
            Sua missão é analisar transcrições de vídeos do YouTube com marcações de tempo precisas e extrair APENAS momentos com altíssimo potencial de viralização.

            DIRETRIZES FUNDAMENTAIS PARA OS CORTES:
            1. ESTRUTURA NARRATIVA COMPLETA:
               - Início (Gancho / Hook): Deve prender a atenção nos primeiros 3 segundos com uma quebra de padrão, pergunta provocativa ou afirmação de impacto.
               - Meio (Desenvolvimento): Ritmo ágil, sem enrolação ou silêncios mortos.
               - Fim (Desfecho): Conclusão satisfatória, revelação ou impacto emocional marcante. Nunca corte no meio de uma frase!
            2. PRECISÃO CIRÚRGICA DE TIMESTAMPS:
               - O início (startSeconds) deve coincidir com o início da frase que introduz o assunto.
               - O término (endSeconds) deve coincidir com o ponto final do pensamento/história.
            3. CRITÉRIOS DE PONTUAÇÃO (0 a 100):
               - 95 a 100: Conteúdo extraordinário, gancho perfeito, história magnética de alta retenção.
               - 90 a 94: Excelente clipe, história bem estruturada, ótimo gancho.
               - Abaixo de 90: Boas falas, mas sem arco dramático completo ou gancho fraco.
            4. FORMATO DE SAÍDA:
               - Responda OBRIGATORIAMENTE em JSON válido com a lista de candidatos no formato especificado.
            """;

    public static String buildUserPrompt(String videoTitle, String videoAuthor, String formattedTranscript) {
        return """
                VÍDEO: "%s" por %s

                TRANSCRIÇÃO COM TIMESTAMPS:
                %s

                Identifique todos os momentos candidatos a corte viral.
                Lembre-se: Seja rigoroso na nota de viralização (0 a 100) e forneça início, meio e fim completos.
                """.formatted(videoTitle, videoAuthor, formattedTranscript);
    }
}
