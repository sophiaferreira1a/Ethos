# Frontend da coordenação — guia e contrato com a API

Dono: Francisco. Arquivos em `coordenacao/`:

| Arquivo | Papel |
|---|---|
| `index.html` | Login da coordenação, dashboard, painel de detalhes e confirmação |
| `api.js` | Única camada que fala com o backend (fetch, tratamento de erros, normalização e dados fictícios para quando o backend está fora) |
| `coordenacao.js` | Interface: filtros, lista, detalhes, ações |
| `coordenacao.css` | Estilos próprios; a base vem de `/style.css` (mesma identidade do professor) |

## Como rodar

1. Suba o backend (`docs/BACKEND.md`) em `http://localhost:8080`.
2. Abra a pasta do projeto (a raiz `Ethos/`) com o Live Server e acesse **`http://localhost:5500/coordenacao/`**.
   Os caminhos da página são absolutos (`/style.css`, `/coordenacao/api.js`) para funcionar também na Vercel, que serve `/coordenacao` sem barra no final.
   Use `localhost`, não `127.0.0.1`: com `SameSite=Lax`, `127.0.0.1:5500 → localhost:8080` é cross-site e o cookie de sessão não é enviado.
3. Entre com `coordenacao@escola.com` / `Ocorrencia@2026`.

Se o backend não responder **na abertura da página**, a tela usa dados fictícios em memória e aceita qualquer e-mail válido com senha de 4+ caracteres. Não há aviso visual desse modo, então confira se o backend está rodando antes de testar de verdade. Uma queda de rede depois do login vira mensagem de erro, sem trocar para os dados fictícios. Antes do deploy, deixe `CONFIG.USAR_MOCK_SE_FALHAR = false`. Troque também `CONFIG.API_URL` em `api.js` pela URL do Render (ou `"/api"`, se o time ligar o rewrite descrito em `docs/DEPLOY.md`).

## Uso do CRUD

| Operação | Endpoint | Onde aparece na tela |
|---|---|---|
| Ler lista | `GET /ocorrencias` | Tabela, busca por aluno, filtros, paginação |
| Ler um | `GET /ocorrencias/{id}` | Painel "Detalhes" |
| Atualizar | `PUT /ocorrencias/{id}` | "Corrigir dados" no painel |
| Excluir | `DELETE /ocorrencias/{id}` | "Excluir ocorrência" no painel (com confirmação) |
| Lançar | `PATCH /ocorrencias/{id}/lancada` | Botão na linha e no painel (com confirmação) |
| Criar | `POST /ocorrencias` | Não existe aqui: é do professor (a coordenação recebe 403) |

## Como a tela usa a API atual

Conferido contra `OcorrenciaService`, `OcorrenciaResponse`, `FiltroOcorrencia`, `HistoricoController` e `IndicadoresController`.

### `GET /ocorrencias`

Enviado ao backend, com os nomes do `FiltroOcorrencia`: `tipo`, `status`, `dataInicio`, `dataFim` (só quando preenchidos). Vão também `page`, `size` e `sort`, que hoje são ignorados.

O service devolve **a lista inteira** (sem paginação), então a tela pagina, ordena e aplica no navegador os filtros que dependem de nome:

- **Turma:** o backend filtra por `turmaId` (UUID) e não existe `GET /turmas` para descobrir os ids. A tela compara pelo `turmaNome`.
- **Aluno:** o backend só filtra por `alunoId`. A busca da coordenação é por parte do nome, sem acento.

Se o volume crescer, o caminho é o Mateus aceitar `Pageable` e um filtro por nome; a tela já entende `Page` do Spring.

### Resposta (`OcorrenciaResponse`)

Campos planos: `alunoId`, `alunoNome`, `turmaNome`, `professorNome`, `statusLancamento`, `dataLancamento`, `responsavelLancamentoNome`, `criadoEm`… O `normalizarOcorrencia` de `api.js` também aceita o formato aninhado (`aluno: {id, nome}`), caso mude.

### `PUT /ocorrencias/{id}`

Corpo igual ao `OcorrenciaRequest` (o mesmo do POST do professor). A tela não troca aluno nem turma.

```json
{"aluno": {"id": "…", "nome": "…"}, "turma": "7A", "tipo": "CELULAR",
 "descricao": "…", "medidasTomadas": "…", "data": "2026-10-07"}
```

### `PATCH /ocorrencias/{id}/lancada`

Sem corpo; data e responsável vêm da sessão no backend. Já lançada → 409, a tela avisa e recarrega.

### `DELETE /ocorrencias/{id}`

Ver o primeiro item de "Pendências" abaixo: hoje sempre responde 409.

### `GET /indicadores` e `GET /ocorrencias/{id}/historico`

Usados como estão. Dos indicadores a tela lê só `total`, `naoLancadas` e `lancadas` (os agrupamentos `porTurma`, `porTipo` e `porPeriodo` ficam para os gráficos futuros). No histórico, os textos fixos de `detalhes` gravados pelo service (`"lancada pela coordenacao"`…) não são exibidos, porque repetem a ação.

## Pendências no backend que afetam esta tela

- **Falta o `OcorrenciaController` (Mateus).** O `OcorrenciaService` está pronto, mas nenhum controller expõe `/ocorrencias`; até ele entrar, a lista mostra "ainda não está disponível na API". Os métodos do service já batem com o que a tela chama: `listar(FiltroOcorrencia)`, `buscar`, `atualizar`, `excluir`, `lancar`.
- **`DELETE` sempre falha.** `excluir` grava `OCORRENCIA_EXCLUIDA` em `historicos` e apaga a ocorrência, mas `historicos.ocorrencia_id` referencia `ocorrencias(id)`: a FK barra e vira 409. Toda ocorrência tem ao menos o `OCORRENCIA_CRIADA`, então nenhuma pode ser excluída. Sugestão: exclusão lógica (coluna `excluida_em`, migration V10+, e o `GET` filtrando as excluídas). A tela já mostra uma mensagem clara nesse caso.
- **Sem `GET /turmas` e `GET /alunos`.** Os services existem, mas sem controller. O filtro de turma usa a lista padrão do `api.js` (`TURMAS_PADRAO`); manter igual às turmas do banco.
- **Desfazer lançamento:** não há endpoint para voltar a `NAO_LANCADA`. A confirmação antes do PATCH reduz cliques por engano.
