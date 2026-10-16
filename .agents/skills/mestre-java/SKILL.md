---
name: mestre-java
description: >-
  Use esta skill quando precisar projetar, implementar ou refatorar código Java moderno utilizando Java 25, explorando Virtual Threads, Records, Pattern Matching, JPMS (Java Platform Module System) e alta performance.
---

# Mestre do Java 25

Esta skill orienta a escrita e evolução de código Java idiomático, performático e moderno, aproveitando ao máximo as capacidades do Java 25 LTS.

## Diretrizes e Recursos Essenciais do Java 25

### 1. Concorrência com Virtual Threads
- Use Virtual Threads para tarefas I/O-bound (como execução de subprocessos FFmpeg, leitura de disco, chamadas HTTP para APIs de IA):
  ```java
  try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      executor.submit(() -> runFfmpegCommand(command));
  }
  ```
- Evite `ThreadLocal` ou pools fixos pesados para operações I/O; prefira o modelo thread-per-task com Virtual Threads.
- Cuidado com *pinning*: evite blocos `synchronized` longos segurando I/O; prefira `ReentrantLock` se necessário.

### 2. Modelagem de Domínio com Records e Sealed Types
- Modele dados imutáveis com **Records**:
  ```java
  public record ClipSegment(double startSeconds, double endSeconds, String title, double viralityScore) {}
  ```
- Use **Sealed Interfaces** para estados do pipeline:
  ```java
  public sealed interface ProcessingState permits Idle, Processing, Completed, Failed {}
  public record Idle() implements ProcessingState {}
  public record Processing(double progress, String currentStep) implements ProcessingState {}
  public record Completed(File outputFile) implements ProcessingState {}
  public record Failed(String errorMessage, Throwable cause) implements ProcessingState {}
  ```

### 3. Pattern Matching Moderno
- Use pattern matching avançado em `switch` e `instanceof`:
  ```java
  String message = switch (state) {
      case Idle _ -> "Pronto";
      case Processing p -> "Processando: %s (%.0f%%)".formatted(p.currentStep(), p.progress() * 100);
      case Completed c -> "Vídeo exportado: " + c.outputFile().getName();
      case Failed f -> "Erro: " + f.errorMessage();
  };
  ```

### 4. Boas Práticas JavaFX com Java 25
- **Desacoplamento de UI e Background**:
  - Toda operação demorada roda fora da UI thread via Virtual Thread.
  - Atualizações visuais usam `javafx.application.Platform.runLater(Runnable)`.
- **JPMS e Reflexão**:
  - Todo pacote com controllers FXML deve estar aberto para `javafx.fxml` no `module-info.java`.
  - Componentes gráficos e temas (AtlantaFX) devem ser devidamente declarados nas dependências de módulo.
