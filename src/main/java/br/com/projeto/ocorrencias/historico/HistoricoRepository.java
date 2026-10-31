package br.com.projeto.ocorrencias.historico;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HistoricoRepository extends JpaRepository<Historico, UUID> {

    /**
     * Linha do tempo de uma ocorrencia, da mais antiga para a mais recente.
     * O join com {@code Usuario} e feito por id (sem associacao mapeada) e e somente leitura.
     */
    @Query("""
            select new br.com.projeto.ocorrencias.historico.HistoricoRegistro(
                h.id, h.acao, h.detalhes, h.dataHora, u.id, u.nome)
            from Historico h
            join Usuario u on u.id = h.usuarioId
            where h.ocorrenciaId = :ocorrenciaId
            order by h.dataHora asc, h.id asc
            """)
    List<HistoricoRegistro> buscarPorOcorrencia(@Param("ocorrenciaId") UUID ocorrenciaId);

    /**
     * Existencia da ocorrencia sem depender da entidade/repository do modulo de ocorrencias.
     */
    @Query(value = "select exists (select 1 from ocorrencias where id = :ocorrenciaId)", nativeQuery = true)
    boolean ocorrenciaExiste(@Param("ocorrenciaId") UUID ocorrenciaId);
}
