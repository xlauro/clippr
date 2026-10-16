---
name: testes
description: >-
  Use esta skill quando precisar criar, executar, refatorar ou auditar testes automatizados no projeto (JUnit 5, testes unitários, testes de integração, testes com simulação de subprocessos FFmpeg e testes de controllers/UI).
---

# Especialista em Testes Automatizados (JUnit 5 & JavaFX)

Esta skill define os padrões e procedimentos para garantir que o Clippr tenha cobertura confiável, rápida e de alta qualidade.

## Princípios de Testes

1. **Rápido e Confiável**: Testes unitários devem rodar em milissegundos sem depender de binários externos ou conexões reais de rede.
2. **Isolamento de Efeitos Colaterais**: Operações que invocam `ffmpeg` ou APIs externas de IA devem ser isoladas por interfaces para permitir testes unitários determinísticos.
3. **Legibilidade BDD/Given-When-Then**:
   - `DisplayName` descritivo em português.
   - Nomes de métodos claros indicando o comportamento esperado.

## Estratégias por Camada

### 1. Testes de Domínio e Records
```java
@Test
@DisplayName("Deve calcular duração correta do clipe em segundos")
void shouldCalculateCorrectDuration() {
    var segment = new ClipSegment(10.5, 25.0, "Gancho Principal", 0.95);
    assertEquals(14.5, segment.duration(), 0.001);
}
```

### 2. Testes de Integração e Parsers FFmpeg
- Para validar parsers de saída do FFmpeg (ex: linhas de progresso `frame= 123 fps= 30 time=00:01:23.45 bitrate= 2500kbits/s`), use strings de teste conhecidas:
```java
@Test
@DisplayName("Deve extrair tempo decorrido do log de progresso do FFmpeg")
void shouldParseFfmpegProgressLine() {
    String logLine = "frame= 300 fps= 60 time=00:00:05.00 bitrate= 3500kbits/s";
    Duration elapsed = FfmpegOutputParser.parseTime(logLine);
    assertEquals(Duration.ofSeconds(5), elapsed);
}
```

### 3. Testes de UI (JavaFX)
- Para testes de controllers sem abrir janelas gráficas completas, teste os modelos e a lógica de apresentação isoladamente.
- Se for testar JavaFX em ambiente de CI/headless, garanta a propriedade de sistema `-Djava.awt.headless=true` ou `-Dtestfx.robot=glass`.

## Comandos de Execução
- Rodar toda a suite: `./gradlew test`
- Rodar classe específica: `./gradlew test --tests "com.laurosantos.clippr.AppTest"`
- Rodar com logs detalhados: `./gradlew test --info`
