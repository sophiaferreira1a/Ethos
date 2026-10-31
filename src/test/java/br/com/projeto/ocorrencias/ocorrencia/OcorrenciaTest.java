package br.com.projeto.ocorrencias.ocorrencia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import br.com.projeto.ocorrencias.aluno.Aluno;
import br.com.projeto.ocorrencias.turma.Turma;
import br.com.projeto.ocorrencias.usuario.Role;
import br.com.projeto.ocorrencias.usuario.Usuario;

class OcorrenciaTest {

    @Test
    void nasceNaoLancadaSemDataNemResponsavel() {
        Ocorrencia ocorrencia = nova();

        assertThat(ocorrencia.getStatusLancamento()).isEqualTo(StatusLancamento.NAO_LANCADA);
        assertThat(ocorrencia.getDataLancamento()).isNull();
        assertThat(ocorrencia.getResponsavelLancamento()).isNull();
        assertThat(ocorrencia.getTurma().getNome()).isEqualTo("6A");
    }

    @Test
    void lancarExigeDataEResponsavelJuntos() {
        Ocorrencia ocorrencia = nova();
        Usuario coordenacao = usuario("Ana Coordenacao", "coordenacao@escola.com", Role.COORDENACAO);
        Instant quando = Instant.parse("2026-10-30T15:00:00Z");

        assertThatThrownBy(() -> ocorrencia.lancar(null, quando))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ocorrencia.lancar(coordenacao, null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(ocorrencia.getStatusLancamento()).isEqualTo(StatusLancamento.NAO_LANCADA);
        assertThat(ocorrencia.getDataLancamento()).isNull();
        assertThat(ocorrencia.getResponsavelLancamento()).isNull();
    }

    @Test
    void lancarPreencheStatusDataEResponsavelENaoPermiteDeNovo() {
        Ocorrencia ocorrencia = nova();
        Usuario coordenacao = usuario("Ana Coordenacao", "coordenacao@escola.com", Role.COORDENACAO);
        Instant quando = Instant.parse("2026-10-30T15:00:00Z");

        ocorrencia.lancar(coordenacao, quando);

        assertThat(ocorrencia.getStatusLancamento()).isEqualTo(StatusLancamento.LANCADA);
        assertThat(ocorrencia.getDataLancamento()).isEqualTo(quando);
        assertThat(ocorrencia.getResponsavelLancamento()).isSameAs(coordenacao);
        assertThatThrownBy(() -> ocorrencia.lancar(coordenacao, quando))
                .isInstanceOf(ConflitoOcorrenciaException.class)
                .hasMessage("Ocorrencia ja lancada");
    }

    private static Ocorrencia nova() {
        Turma turma = new Turma("6A");
        ReflectionTestUtils.setField(turma, "id", UUID.randomUUID());
        Aluno aluno = new Aluno("Ana Clara Souza", turma);
        ReflectionTestUtils.setField(aluno, "id", UUID.randomUUID());
        Usuario professor = usuario("Maria Silva", "professor@escola.com", Role.PROFESSOR);
        return new Ocorrencia(aluno, professor, TipoOcorrencia.INDISCIPLINA,
                "Aluno interrompeu a aula varias vezes.", "Conversa com o aluno", LocalDate.of(2026, 10, 30));
    }

    private static Usuario usuario(String nome, String email, Role role) {
        Usuario usuario = new Usuario(nome, email, "hash", role);
        ReflectionTestUtils.setField(usuario, "id", UUID.randomUUID());
        return usuario;
    }
}
