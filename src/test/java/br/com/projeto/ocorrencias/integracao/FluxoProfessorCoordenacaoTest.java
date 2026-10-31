package br.com.projeto.ocorrencias.integracao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

/**
 * Fluxo completo da documentacao (secoes 1 e 14): Professor registra -> Coordenacao
 * visualiza e lanca -> historico e indicadores refletem.
 *
 * <p><b>Desabilitado de proposito:</b> depende de {@code POST /ocorrencias} e
 * {@code PATCH /ocorrencias/{id}/lancada}, que sao do Mateus e ainda nao estao na
 * {@code main}. Quando a branch dele entrar:</p>
 * <ol>
 *   <li>confirmar o JSON de {@code POST /ocorrencias} (o corpo abaixo e um palpite
 *       baseado no exemplo da secao 5: {@code alunoId}, {@code tipo}, {@code descricao},
 *       {@code medidasTomadas}, {@code data});</li>
 *   <li>remover o {@code @Disabled} e rodar com {@code TEST_DB_URL}.</li>
 * </ol>
 */
@Disabled("Habilitar quando feature/ocorrencias-mateus estiver na main e o payload de POST /ocorrencias for confirmado")
@TesteIntegracao
class FluxoProfessorCoordenacaoTest extends BaseIntegracaoTest {

    @Test
    void professorRegistraCoordenacaoLancaEHistoricoEIndicadoresAcompanham() throws Exception {
        limparTabelas();
        UUID turmaId = criarTurma("2A");
        UUID alunoId = criarAluno("Joao Silva", turmaId);

        MockHttpSession professor = login(EMAIL_PROFESSOR);
        MockHttpSession coordenacao = login(EMAIL_COORDENACAO);

        // 1. Professor registra (ajustar o corpo conforme o DTO do Mateus).
        MvcResult criada = mvc.perform(post("/ocorrencias").session(professor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "alunoId": "%s",
                                  "tipo": "COMPORTAMENTAL",
                                  "descricao": "Conversa excessiva durante a aula",
                                  "medidasTomadas": "Conversa com o aluno",
                                  "data": "2026-10-07"
                                }
                                """.formatted(alunoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusLancamento").value("NAO_LANCADA"))
                .andReturn();
        String ocorrenciaId = JsonPath.read(criada.getResponse().getContentAsString(), "$.id");

        // 2. Professor nao executa acoes da coordenacao.
        mvc.perform(patch("/ocorrencias/" + ocorrenciaId + "/lancada").session(professor))
                .andExpect(status().isForbidden());
        mvc.perform(get("/ocorrencias/" + ocorrenciaId + "/historico").session(professor))
                .andExpect(status().isForbidden());

        // 3. Antes de lancar: 1 nao lancada e historico so com a criacao.
        mvc.perform(get("/indicadores").session(coordenacao))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.naoLancadas").value(1))
                .andExpect(jsonPath("$.lancadas").value(0));
        mvc.perform(get("/ocorrencias/" + ocorrenciaId + "/historico").session(coordenacao))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].acao").value("OCORRENCIA_CRIADA"))
                .andExpect(jsonPath("$[0].usuario.nome").value("Maria Silva"));

        // 4. Coordenacao lanca.
        mvc.perform(patch("/ocorrencias/" + ocorrenciaId + "/lancada").session(coordenacao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusLancamento").value("LANCADA"))
                .andExpect(jsonPath("$.responsavelLancamento.nome").value("Ana Coordenacao"));

        // 5. Depois: indicadores e historico refletem o lancamento.
        mvc.perform(get("/indicadores").session(coordenacao))
                .andExpect(jsonPath("$.naoLancadas").value(0))
                .andExpect(jsonPath("$.lancadas").value(1));
        mvc.perform(get("/ocorrencias/" + ocorrenciaId + "/historico").session(coordenacao))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].acao").value("OCORRENCIA_LANCADA"))
                .andExpect(jsonPath("$[1].usuario.nome").value("Ana Coordenacao"));
    }
}
