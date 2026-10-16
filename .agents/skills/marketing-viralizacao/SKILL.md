---
name: marketing-viralizacao
description: >-
  Use esta skill para orientar estratégias de marketing web, crescimento e viralização de vídeos curtos (TikTok, Instagram Reels, YouTube Shorts), incluindo regras de ganchos (hooks), retenção, legendagem dinâmica, corte de momentos virais e otimização para algoritmos.
---

# Mestre de Marketing Web e Viralização de Vídeos

Esta skill compila as regras, heurísticas e padrões da indústria de conteúdo de alta retenção para orientar as decisões do Clippr na criação de cortes que viralizam na web.

## Pilares da Viralização de Vídeos Curtos (Short-Form)

### 1. A Regra de Ouro dos Primeiros 3 Segundos (The Hook)
- **Quebra de Padrão (Pattern Interrupt)**: O início do clipe não pode começar com "Olá pessoal" ou silêncio. Deve começar diretamente na ação, numa pergunta polêmica ou numa afirmação contraintuitiva.
- **Curiosity Gap**: Apresentar um mistério ou uma pergunta que só será respondida no final do vídeo.
- **Frame de Capa (Thumbnail)**: O corte deve ter um frame expressivo de alta energia visual selecionado para a miniatura.

### 2. Retenção e Ritmo (Pacing)
- **Corte de Silêncios e Vícios de Linguagem**:
  - Eliminar pausas superiores a 400-500ms usando filtros de detecção de silêncio (`silencedetect` no FFmpeg).
  - Remover hesitações ("humm", "ééé") para manter a densidade de informação alta.
- **Duração Ideal para Viralização**:
  - TikTok / Reels: Entre 30 e 60 segundos tem a maior taxa de conclusão (completion rate), métrica número 1 dos algoritmos.
  - YouTube Shorts: Até 50 segundos.

### 3. Formatação e Layout Visual (9:16 Vertical)
- **Enquadramento Inteligente**:
  - Vídeos horizontais (16:9) devem ser recortados ou ajustados para vertical (9:16 - 1080x1920).
  - Manter o rosto do interlocutor centralizado no terço superior da tela.
- **Legendas Dinâmicas (Karaokê / Pop-in)**:
  - Mais de 70% dos vídeos em redes sociais são consumidos sem som inicial.
  - Legendas palavra por palavra com destaque em cores fortes (amarelo, verde neon) aumentam a retenção em até 40%.

### 4. Heurísticas para o Motor de IA do Clippr
Ao analisar a transcrição e o áudio para sugerir cortes:
1. **Picos de Volume e Energia de Áudio**: Risos, aplausos ou elevações de tom de voz indicam momentos de clímax.
2. **Densidade Semântica**: Identificar histórias completas (Início instigante -> Desenvolvimento rápido -> Conclusão surpreendente).
3. **Virality Score**: Calcular pontuação baseada em:
   - Força do gancho inicial (0 a 10).
   - Ausência de períodos mortos de áudio.
   - Presença de palavras de alto impacto emocional.
