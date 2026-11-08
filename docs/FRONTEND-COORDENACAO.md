# Frontend da coordenação — guia e contrato com a API

Dono: Francisco. Arquivos em `coordenacao/`:

| Arquivo | Papel |
|---|---|
| `index.html` | Login, abas **Ocorrências** e **Cadastros**, painel de detalhes e diálogos |
| `api.js` | Única camada que fala com o backend (fetch, erros, normalização e dados fictícios) |
| `coordenacao.js` | Interface: filtros, lista, detalhes, abas, CRUD e movimentação em lote |
| `coordenacao.css` | Estilos próprios; a base vem de `/style.css` (mesma identidade do professor) |

## Como rodar

1. Suba o backend (`docs/BACKEND.md`) em `http://localhost:8080`.
2. Abra a pasta do projeto (a raiz `Ethos/`) com o Live Server e acesse **`http://localhost:5500/coordenacao/`**.
   Os caminhos da página são absolutos (`/style.css`, `/coordenacao/api.js`) para funcionar também na Vercel, que serve `/coordenacao` sem barra no final.
   Use `localhost`, não `127.0.0.1`: com `SameSite=Lax`, `127.0.0.1:5500 → localhost:8080` é cross-site e o cookie de sessão não é enviado.
3. Entre com `coordenacao@escola.com` / `Ocorrencia@2026`.

Se o backend não responder **na abertura da página**, a tela usa dados fictícios em memória e aceita qualquer e-mail válido com senha de 4+ caracteres. Não há aviso visual desse modo, então confira se o backend está rodando antes de testar de verdade. Uma queda de rede depois do login vira mensagem de erro, sem trocar para os dados fictícios. Antes do deploy, deixe `CONFIG.USAR_MOCK_SE_FALHAR = false`. Troque também `CONFIG.API_URL` em `api.js` pela URL do Render (ou `"/api"`, se o time ligar o rewrite descrito em `docs/DEPLOY.md`).

## Navegação

- Aba principal **Ocorrências**: fila de pendências, busca, filtros, tabela, detalhes, lançamento e exclusão.
- Aba principal **Cadastros**, com sub-abas:
  - **Turmas:** criar, renomear e excluir turmas.
  - **Alunos:** criar, editar e excluir alunos (exclusão lógica).
  - **Montar turmas:** selecionar vários alunos e movê-los para uma turma de destino.

## Uso do CRUD

| Operação | Endpoint | Onde aparece na tela |
|---|---|---|
| Ler lista de ocorrências | `GET /ocorrencias` | Tabela, busca por aluno, filtros, paginação |
| Ler uma ocorrência | `GET /ocorrencias/{id}` | Painel "Detalhes" |
| Atualizar ocorrência | `PUT /ocorrencias/{id}` | "Corrigir dados" no painel |
| Excluir ocorrência | `DELETE /ocorrencias/{id}` | "Excluir ocorrência" no painel (com confirmação) |
| Lançar | `PATCH /ocorrencias/{id}/lancada` | Botão na linha e no painel (com confirmação) |
| Criar ocorrência | `POST /ocorrencias` | Não existe aqui: é do professor (a coordenação recebe 403) |
| Listar turmas/alunos | `GET /turmas`, `GET /alunos` | Filtros e sub-abas de cadastro |
| Criar/editar/excluir turma | `POST/PUT/DELETE /turmas` | Sub-aba "Turmas" |
| Criar/editar/excluir aluno | `POST/PUT/DELETE /alunos` | Sub-aba "Alunos" |
| Mover alunos em lote | `PATCH /alunos/turma` | Sub-aba "Montar turmas" |

## Como a tela usa a API

Conferido contra `OcorrenciaService`, `OcorrenciaResponse`, `FiltroOcorrencia`, `AlunoController`, `TurmaController`, `HistoricoController` e `IndicadoresController`.

### `GET /ocorrencias`

Enviado ao backend, com os nomes do `FiltroOcorrencia`: `tipo`, `status`, `dataInicio`, `dataFim` (só quando preenchidos). Vão também `page`, `size` e `sort`, que hoje são ignorados.

O service devolve **a lista inteira** (sem paginação), então a tela pagina, ordena e aplica no navegador os filtros que dependem de nome:

- **Turma:** o backend filtra por `turmaId` (UUID); a tela compara pelo `turmaNome` porque o filtro é montado a partir de `GET /turmas`.
- **Aluno:** o backend só filtra por `alunoId`. A busca da coordenação é por parte do nome, sem acento.

Se o volume crescer, o caminho é o backend aceitar `Pageable` e um filtro por nome; a tela já entende `Page` do Spring.

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

Cancelamento lógico (V10, `cancelada_em`): responde 204, a ocorrência some da listagem e o histórico fica. Já lançada ou já cancelada → 409; a tela avisa e recarrega.

### `GET /indicadores` e `GET /ocorrencias/{id}/historico`

Usados como estão. Dos indicadores a tela lê só `total`, `naoLancadas` e `lancadas` (os agrupamentos `porTurma`, `porTipo` e `porPeriodo` ficam para os gráficos futuros). No histórico, os textos fixos de `detalhes` gravados pelo service (`"lancada pela coordenacao"`…) não são exibidos, porque repetem a ação.

## Cadastros

### Turmas

- O nome precisa ser único (sem diferenciar maiúsculas). Duplicado → `409` e a mensagem aparece no diálogo.
- A exclusão é bloqueada pelo backend quando existe aluno (inclusive excluído) ou ocorrência vinculada. A tela mostra a mensagem do `409`.
- Turmas são reutilizadas todo ano; ainda não há campo `ano_letivo`.

### Alunos

- O aluno sempre pertence a uma turma.
- A exclusão é **lógica**: `DELETE /alunos/{id}` grava `excluido_em`, o aluno some de `GET /alunos` e não pode ser usado em novas ocorrências. Ocorrências e históricos ficam.
- O pop-up de exclusão usa **Voltar** (azul) e **Excluir** (vermelho).
- A sub-aba carrega `GET /alunos` uma vez e filtra por nome/turma no navegador.

### Montar turmas

- A tela lista os alunos ativos, permite filtrar pela turma de origem, marcar vários e escolher a turma de destino.
- `PATCH /alunos/turma` recebe `{ "alunoIds": ["…"], "turmaId": "…" }` e é atômico: se um aluno não existir ou estiver excluído, ninguém é movido.
- Ocorrências antigas preservam a turma em que foram registradas; a movimentação só muda o vínculo atual do aluno.

## Pendências

- **Desfazer lançamento:** não há endpoint para voltar a `NAO_LANCADA`. A confirmação antes do PATCH reduz cliques por engano.
- **Filtros de turma e aluno no navegador:** dá para mandar `turmaId`/`alunoId` ao backend em vez de filtrar por nome na tela. Só vale a pena se o volume de ocorrências crescer.
- **Ano letivo:** a movimentação anual usa as mesmas turmas. Se a escola precisar separar históricos por ano, será necessário um campo novo e migration.
