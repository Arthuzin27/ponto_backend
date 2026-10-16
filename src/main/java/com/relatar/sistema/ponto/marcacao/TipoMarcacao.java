package com.relatar.sistema.ponto.marcacao;

public enum TipoMarcacao {
    ENTRADA,
    SAIDA;

    public TipoMarcacao oposto() {
        return this == ENTRADA ? SAIDA : ENTRADA;
    }
}
