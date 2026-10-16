package com.relatar.sistema.ponto.marcacao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarcacaoRepository extends JpaRepository<Marcacao, Long> {

    List<Marcacao> findByUsuarioIdAndDataReferenciaOrderByInstanteAsc(Long usuarioId, LocalDate dataReferencia);

    List<Marcacao> findByUsuarioIdAndDataReferenciaBetweenOrderByInstanteAsc(Long usuarioId, LocalDate de,
            LocalDate ate);

    Optional<Marcacao> findFirstByUsuarioIdOrderByInstanteDesc(Long usuarioId);

    /** Marcacoes de todos os usuarios no periodo (paineis da equipe). */
    List<Marcacao> findByDataReferenciaBetweenOrderByInstanteAsc(LocalDate de, LocalDate ate);
}
