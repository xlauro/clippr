# Diretrizes do Projeto Clippr (Java 25 + JavaFX + FFmpeg + IA)

## 1. Regra de Ouro: Foco Estrito e Antidistração
- **Aderência Total ao Escopo Solicitado**: Execute exatamente o que o usuário pediu. Não invente requisitos, não adicione bibliotecas não solicitadas e não faça refatorações cosméticas em código que não faz parte da demanda.
- **Eficiência e Velocidade**: Não leia arquivos desnecessários (vídeos, logs gigantescos, caches do build). Use comandos cirúrgicos (`find`, `grep`, `head`, `tail`) em vez de varreduras amplas.
- **Sem Perda de Tempo com Explicações Óbvias**: Responda com clareza, objetividade e foco nas decisões técnicas e no código executado.

## 2. Padrões Técnicos (Java 25)
- **Modern Java Idioms**: Utilize recursos modernos do Java 25:
  - *Virtual Threads* (`Thread.ofVirtual()` ou `Executors.newVirtualThreadPerTaskExecutor()`) para operações I/O-bound (execução assíncrona de comandos FFmpeg, chamadas de rede para APIs de IA).
  - *Records* para DTOs, modelos de dados imutáveis (ex: `VideoMetadata`, `ClipRange`, `AiSegment`).
  - *Pattern Matching* com `switch` e `instanceof`.
  - *Sealed Interfaces/Classes* para modelar estados finitos (ex: `ProcessingStatus`).
- **JavaFX & Concorrência**:
  - **NUNCA** execute comandos FFmpeg ou chamadas de IA na JavaFX Application Thread (UI Thread).
  - Sempre processe em threads separadas (preferencialmente Virtual Threads) e atualize componentes de UI exclusivamente via `Platform.runLater()`.
  - Mantenha o design responsivo com AtlantaFX e layouts FXML bem organizados.
- **JPMS (Java Platform Module System)**:
  - Respeite as regras do `module-info.java`. Ao criar novos pacotes que necessitem de injeção FXML ou reflexão, certifique-se de declarar `opens` apropriado.

## 3. Arquitetura do Clippr
- `com.laurosantos.clippr`: Ponto de entrada (`App`).
- `com.laurosantos.clippr.ui`: Controllers FXML e componentes visuais.
- `com.laurosantos.clippr.ffmpeg`: Camada de execução e parser de subprocessos FFmpeg via `ProcessBuilder`.
- `com.laurosantos.clippr.ai`: Interfaces e implementações plugáveis de IA (Local Ollama / APIs de Nuvem).
- `com.laurosantos.clippr.domain` / `model`: Records e regras de negócio de corte/clipping.

## 4. Testes e Validação
- Sempre rode `./gradlew test` ou `./gradlew compileJava` após alterações estruturais.
- Garanta que código novo seja acompanhado por testes automatizados pertinentes com JUnit 5.
