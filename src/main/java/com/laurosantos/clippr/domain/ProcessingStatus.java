package com.laurosantos.clippr.domain;

public enum ProcessingStatus {
    PENDING("Pendente"),
    FETCHING_TRANSCRIPT("Baixando transcrição"),
    ANALYZING_AI("Analisando com IA"),
    PENDING_APPROVAL("Aguardando aprovação"),
    DOWNLOADING_VIDEO("Baixando vídeo"),
    CUTTING_FFMPEG("Renderizando cortes"),
    COMPLETED("Concluído"),
    DISCARDED_LOW_SCORE("Descartado (Nota < 90)"),
    FAILED("Falhou");

    private final String description;

    ProcessingStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
