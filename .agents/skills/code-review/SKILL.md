---
name: code-review
description: >-
  Use esta skill para realizar revisões de código críticas e profundas (code review), avaliando arquitetura, segurança, concorrência, tratamento de recursos nativos (FFmpeg) e boas práticas em Java 25 e JavaFX.
---

# Code Review Master

Esta skill define a lista de verificação e os critérios de qualidade para aprovação de código no Clippr.

## Checklist de Revisão

### 1. Gestão de Processos Nativos e Recursos (FFmpeg / I/O)
- [ ] **Vazamento de Processos**: Todo `Process` gerado via `ProcessBuilder` possui mecanismo de finalização garantido (`process.destroyForcibly()` em caso de cancelamento ou falha)?
- [ ] **Consumo de Streams**: Os streams `stdout` e `stderr` do subprocesso estão sendo consumidos em threads separadas para evitar travamento de buffer (*buffer deadlock*) do sistema operacional?
- [ ] **Fechamento de Recursos**: Streams de arquivos, conexões HTTP de IA e executors estão protegidos com `try-with-resources`?

### 2. Concorrência e UI Thread (JavaFX)
- [ ] **Thread Safety**: Nenhuma operação de rede, disco ou FFmpeg é disparada diretamente na JavaFX Application Thread?
- [ ] **Atualização Segura de UI**: Todo toque em componentes gráficos (`Label`, `ProgressBar`, `TextArea`, etc.) fora da thread principal é despachado via `Platform.runLater()`?
- [ ] **Controle de Estado Concorrente**: Variáveis de estado acessadas por múltiplas threads utilizam tipos atômicos (`AtomicBoolean`, `AtomicReference`) ou Records imutáveis?

### 3. Idiomas do Java 25 & Clean Code
- [ ] **Records vs Classes Tradicionais**: DTOs e portadores de dados são modelados como `record` em vez de classes verbosas com getters/setters?
- [ ] **Pattern Matching e Tipos Selados**: Lógicas de múltiplos ramos utilizam `switch` com pattern matching exaustivo em vez de longas cadeias de `if-else`?
- [ ] **Virtual Threads**: Uso apropriado de Virtual Threads para operações bloqueantes, sem pool fixo desnecessário.
- [ ] **Princípio de Responsabilidade Única (SRP)**: Controllers não contêm lógica direta de execução de FFmpeg ou de IA; a lógica fica em serviços especializados.

### 4. Segurança e Validação de Entrada
- [ ] **Sanitização de Comandos**: Argumentos passados para o `ProcessBuilder` são passados como listas de strings e **nunca** concatenados em uma única string em shell (`sh -c ...`), prevenindo injeção de comandos.
- [ ] **Validação de Caminhos**: Os arquivos de entrada e saída são validados antes da execução (existência, permissão de escrita).
