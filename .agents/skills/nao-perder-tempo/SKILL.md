---
name: nao-perder-tempo
description: >-
  Use esta skill para manter foco cirúrgico no objetivo solicitado pelo usuário, prevenindo desperdício de tempo, escopo inflado (scope creep), refatorações não solicitadas e trabalho redundante ou desnecessário.
---

# Foco Estrito e Antidistração (Não Perder Tempo)

Esta skill define o protocolo de disciplina operacional do assistente para garantir máxima velocidade, objetividade e foco total na entrega do que foi expressamente solicitado.

## Protocolo Operacional

### 1. Teste de Aderência ao Pedido (Gatekeeper)
Antes de escrever qualquer linha de código ou executar comandos, responda internamente:
- **"O usuário pediu explicitamente isso?"**
  - Se **SIM**: Prossiga.
  - Se **NÃO**: Pare imediatamente. Não implemente "melhorias extras", não crie arquivos especulativos para o futuro e não altere componentes alheios ao pedido.

### 2. Princípio da Menor Alteração Suficiente (Lean Execution)
- Não reescreva módulos inteiros quando um patch cirúrgico resolve o problema.
- Não adicione bibliotecas de terceiros a menos que seja estritamente indispensável ou requisitado.
- Evite criar abstrações prematuras ou estruturas excessivamente genéricas para um caso de uso simples.

### 3. Evitar Buracos Negros de Informação (Token & Context Waste)
- **Nunca inspecione arquivos pesados de mídia**: Nunca tente abrir `.mp4`, `.mkv`, binários ou logs completos de centenas de megabytes.
- Use filtros precisos (`grep`, `tail -n 20`, flags de busca específicas).
- Se um teste falhar, analise a causa raiz no stack trace imediatamente, em vez de inspecionar aleatoriamente múltiplos arquivos do projeto.

### 4. Ciclo de Entrega Focado
1. Compreenda a demanda exata.
2. Escreva ou edite o código necessário.
3. Valide compilando e rodando os testes (`./gradlew test`).
4. Entregue a resposta de forma concisa e direta, sem rodeios ou floreios desnecessários.
