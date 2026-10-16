package com.relatar.sistema.ponto.marcacao;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Localizacao do dispositivo informada pelo navegador. Por vir do cliente, pode ser falsificada:
 * serve como evidencia complementar, nao como prova.
 *
 * @param precisao raio de incerteza em metros, quando informado
 */
public record Localizacao(
        @NotNull(message = "Informe a latitude.")
        @DecimalMin(value = "-90", message = "Latitude inválida.") @DecimalMax(value = "90", message = "Latitude inválida.")
        Double latitude,
        @NotNull(message = "Informe a longitude.")
        @DecimalMin(value = "-180", message = "Longitude inválida.") @DecimalMax(value = "180", message = "Longitude inválida.")
        Double longitude,
        @PositiveOrZero(message = "Precisão inválida.") Double precisao) {
}
