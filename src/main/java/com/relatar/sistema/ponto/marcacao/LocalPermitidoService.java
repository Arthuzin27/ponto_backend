package com.relatar.sistema.ponto.marcacao;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.relatar.sistema.ponto.common.error.BusinessRuleException;

/**
 * Verifica se o dispositivo esta no local permitido para bater o ponto: a ate {@code toleranciaMetros} do
 * tracado da rua configurada, com precisao de GPS aceitavel. O nome da rua (geocodificacao) e so informativo;
 * a decisao usa a distancia geometrica, que nao depende de servicos externos.
 */
@Service
public class LocalPermitidoService {

    private static final double RAIO_TERRA_METROS = 6_371_000;

    private final MarcacaoProperties.AreaPermitida area;
    private final EnderecoService enderecos;
    private final List<double[]> tracado;

    public LocalPermitidoService(MarcacaoProperties properties, EnderecoService enderecos) {
        this.area = properties.areaPermitida();
        this.enderecos = enderecos;
        this.tracado = lerTracado(area.tracado());
        if (area.habilitada() && tracado.isEmpty()) {
            throw new IllegalStateException("app.marcacao.area-permitida.tracado deve ter ao menos um ponto");
        }
    }

    public boolean habilitado() {
        return area.habilitada();
    }

    /** Avalia a localizacao, sem lancar excecao (usado para mostrar o status na tela). */
    public Verificacao verificar(Localizacao localizacao) {
        String endereco = enderecos.buscar(localizacao.latitude(), localizacao.longitude())
                .map(EnderecoService.Endereco::descricao)
                .orElse(null);
        if (!area.habilitada()) {
            return new Verificacao(endereco, true, null, null, null, null);
        }
        long distancia = Math.round(distanciaAoTracado(localizacao.latitude(), localizacao.longitude()));
        Double precisao = localizacao.precisao();
        if (precisao == null || precisao > area.precisaoMaximaMetros()) {
            String texto = precisao == null ? "desconhecida" : "±" + Math.round(precisao) + " m";
            return new Verificacao(endereco, false, distancia, area.nome(), "LOCALIZACAO_IMPRECISA",
                    "Localização imprecisa (" + texto + "). Ative o GPS/localização precisa do aparelho e aguarde "
                            + "alguns segundos. Computadores sem GPS costumam errar a posição em quilômetros.");
        }
        if (distancia > area.toleranciaMetros()) {
            return new Verificacao(endereco, false, distancia, area.nome(), "FORA_DA_AREA",
                    "Você está a " + formatarDistancia(distancia) + " da " + area.nome()
                            + ". O ponto só pode ser registrado nesse local.");
        }
        return new Verificacao(endereco, true, distancia, area.nome(), null, null);
    }

    /** Avalia e recusa a marcacao quando o local nao e permitido. */
    public Verificacao exigirPermitido(Localizacao localizacao) {
        Verificacao verificacao = verificar(localizacao);
        if (!verificacao.permitido()) {
            throw new BusinessRuleException(verificacao.codigo(), verificacao.mensagem());
        }
        return verificacao;
    }

    /** Menor distancia, em metros, entre o ponto e os segmentos do tracado (projecao local plana). */
    double distanciaAoTracado(double latitude, double longitude) {
        double lat0 = Math.toRadians(latitude);
        double menor = Double.MAX_VALUE;
        double[] anterior = null;
        for (double[] ponto : tracado) {
            double[] atual = projetar(ponto[0], ponto[1], latitude, longitude, lat0);
            if (anterior == null) {
                menor = Math.min(menor, Math.hypot(atual[0], atual[1]));
            } else {
                menor = Math.min(menor, distanciaAoSegmento(anterior, atual));
            }
            anterior = atual;
        }
        return menor;
    }

    /** Coordenadas em metros relativas ao ponto de referencia (que fica na origem). */
    private static double[] projetar(double lat, double lon, double latRef, double lonRef, double latRefRad) {
        double x = Math.toRadians(lon - lonRef) * Math.cos(latRefRad) * RAIO_TERRA_METROS;
        double y = Math.toRadians(lat - latRef) * RAIO_TERRA_METROS;
        return new double[] {x, y};
    }

    /** Distancia da origem ao segmento a-b. */
    private static double distanciaAoSegmento(double[] a, double[] b) {
        double dx = b[0] - a[0];
        double dy = b[1] - a[1];
        double comprimento2 = dx * dx + dy * dy;
        double t = comprimento2 == 0 ? 0 : Math.max(0, Math.min(1, -(a[0] * dx + a[1] * dy) / comprimento2));
        return Math.hypot(a[0] + t * dx, a[1] + t * dy);
    }

    private static List<double[]> lerTracado(List<String> pontos) {
        List<double[]> resultado = new ArrayList<>();
        for (String ponto : pontos) {
            String[] partes = ponto.split(",");
            if (partes.length != 2) {
                throw new IllegalStateException("Ponto invalido no tracado da area permitida: " + ponto);
            }
            resultado.add(new double[] {Double.parseDouble(partes[0].trim()), Double.parseDouble(partes[1].trim())});
        }
        return List.copyOf(resultado);
    }

    private static String formatarDistancia(long metros) {
        return metros >= 1000 ? String.format(Locale.forLanguageTag("pt-BR"), "%.1f km", metros / 1000.0) : metros + " m";
    }

    /**
     * @param endereco        rua, bairro e cidade aproximados, quando disponiveis
     * @param permitido       o ponto pode ser registrado nesta localizacao
     * @param distanciaMetros distancia ate o tracado da rua permitida (null se a regra estiver desligada)
     * @param areaPermitida   nome do local permitido
     * @param codigo          motivo da recusa (LOCALIZACAO_IMPRECISA ou FORA_DA_AREA)
     */
    public record Verificacao(String endereco, boolean permitido, Long distanciaMetros, String areaPermitida,
            String codigo, String mensagem) {
    }
}
