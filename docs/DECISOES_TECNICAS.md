# Decisões técnicas e problemas de integração

Registro do que foi decidido (e do que ficou em aberto) nos módulos de **histórico, indicadores, testes e deploy**.

## 1. Decisões técnicas

| Decisão | Por quê |
|---|---|
| `HistoricoServiceImpl.registrar` usa `Propagation.MANDATORY` | O contrato diz que o histórico grava na mesma transação da ação. Com `REQUIRED`, quem esquecesse o `@Transactional` gravaria histórico solto sem ninguém perceber. Com `MANDATORY` falha alto na hora do desenvolvimento. |
| `saveAndFlush` no registro | Violação de FK (ocorrência/usuário inexistente) estoura dentro de `registrar`, não só no commit — o erro aparece onde está o problema e a transação toda é desfeita. |
| `acao` validada contra `AcaoHistorico` | A coluna é `varchar(40)` livre; sem validação um typo viraria ação inexistente no histórico. |
| Entidade `Historico` é `@Immutable` e sem setters | É trilha de auditoria: nunca se edita. `ocorrenciaId`/`usuarioId` são UUIDs simples (sem `@ManyToOne`), como combinado, para não depender das entidades do Mateus. |
| Consulta de histórico em `HistoricoConsultaService`, separada da interface `HistoricoService` | A interface é o contrato de escrita compartilhado com o Mateus; não foi alterada. |
| Existência da ocorrência checada por SQL nativo (`select exists`) no repository do histórico | Dá `404` correto sem importar nada do pacote `ocorrencia`. |
| Indicadores em SQL nativo via `EntityManager`, só `SELECT` | Pedido na documentação (somente leitura, sem tocar no repository do Mateus). `FILTER (WHERE …)` conta lançadas/não lançadas numa única passada por agrupamento. |
| Agrupamento por período montado a partir de uma enum (`Agrupamento`) | Única parte do SQL que varia; vem de constantes fixas, nunca da requisição. Filtros de data são parâmetros ligados. |
| Sem agrupamento por aluno/ranking | Seção 9: evitar rotular alunos. |
| Erros de parâmetro (`de`, `ate`, `agrupamento`, UUID) tratados **dentro dos controllers do Bernardo** | O `RestExceptionHandler` (Sophia) não trata `MethodArgumentTypeMismatchException`, então cairia no handler genérico e viraria `500`. Em vez de mexer no arquivo dela, os controllers respondem `400`. *Sugestão:* a Sophia pode mover isso para o handler global. |
| Testes de integração ligados por `TEST_DB_URL` (sem Testcontainers) | Não exige alterar o `pom.xml` (é da Sophia), funciona com Postgres local ou Docker, e `mvnw test` segue verde para quem não tem o banco. Trava recusando URL com `supabase`. |
| Dockerfile em duas etapas, usuário sem root, `MaxRAMPercentage=70` | Imagem final só com JRE; o plano gratuito do Render tem 512 MB. |
| Sem migration nova (faixa `V20+` não usada) | Os índices da V1 (`turma_id`, `data`, `status_lancamento`, `historicos(ocorrencia_id)`) já cobrem as consultas. |

## 2. Problemas de integração encontrados

Comparando o frontend do professor (`app.js`, Pedro) com o backend já na base (Sophia). **Nenhum é do módulo do Bernardo**, mas bloqueiam a Fase 4 (integração):

| # | Frontend faz | Backend tem | Quem resolve |
|---|---|---|---|
| 1 | `POST /auth/login` | `POST /login` | Pedro (ajustar rota) |
| 2 | Espera `token` no login e manda `Authorization: Bearer …` | **Sessão por cookie** `JSESSIONID`; a resposta do login não tem `token` | Pedro: remover o Bearer e usar `credentials: "include"` em todo `fetch`. Hoje `sessao.salvar(d.token, …)` guardaria `undefined`. |
| 3 | Lê erro em `dados.mensagem \|\| dados.message` | Erros vêm como `{ "status": …, "erro": "…" }` | Pedro (ler `dados.erro`) |
| 4 | `GET /alunos?busca=&turma=` | **Não existe** na tabela de API (seção 7) | Mateus + Pedro: combinar endpoints de alunos e turmas (listar turmas, buscar alunos) |
| 5 | `tipo` = `DESRESPEITO`, `INDISCIPLINA`, … (8 valores) | Exemplo da doc usa `COMPORTAMENTAL`; a coluna é texto livre | Mateus + Pedro: fixar **uma** lista de tipos (ver seção 3). Os indicadores agrupam pelo texto gravado, então qualquer valor funciona — mas a lista precisa ser única. |
| 6 | Turmas e alunos de mock (`6A`…`3EM`) | `turmas.nome` é `varchar(20)` e único | Ok, mas os nomes reais (ex.: "Maternal III NDO") cabem em 20? Conferir com a escola. |
| 7 | Sem rota de pós-login | `redirectTo` = `/professor` ou `/coordenacao` | Pedro/Francisco: definir as rotas reais |
| 8 | — | `anyRequest().authenticated()` e sem Actuator | Sem endpoint público de health check para o Render. Hoje o Render só confere a porta; funciona. Se quiserem health check HTTP, a Sophia adiciona Actuator + `permitAll` em `/actuator/health`. |

## 3. Lacunas em relação ao sistema atual (fotos)

As fotos mostram que hoje há **um Microsoft Forms por turma**, aberto pelo SharePoint da CSP (Unidade → Segmento → Série/Ano → Turma). O formulário pede: **Unidade, Segmento, Série/Ano, Turma, Data da Ocorrência, Professor, Componente Curricular**, e depois uma lista de estudantes com caixas de seleção de tipos. O texto de orientação do Forms diz que o registro deve ser feito **ao final da última aula do turno**, ser factual e seguir o rito *advertência oral → avisar que será registrado → encaminhar à Coordenação de Formação*.

Comparado ao schema da V1:

- **Componente Curricular** é coletado hoje e **não existe** no schema. Um indicador "por componente" (que a coordenação provavelmente vai querer) exigiria uma coluna nova (migration do Mateus, faixa `V10+`) e um agrupamento a mais em `/indicadores`.
- **Vários tipos por estudante** (caixas de seleção) e **vários estudantes por envio**: o schema tem 1 aluno + 1 tipo por ocorrência. Precisa ser decidido se um envio vira N ocorrências (e então "total" conta ocorrências, não envios) — isso muda como o dashboard deve ser lido.
- **Unidade / Segmento / Série** hoje são perguntas; no sistema novo podem ser derivadas da turma. Se houver várias unidades no mesmo sistema, falta `unidade` em `turmas`.
- **Lista de tipos atual (20)**, como proposta de valores (≤ 30 caracteres, cabem em `varchar(30)`):

| Texto no Forms | Código proposto |
|---|---|
| Agressão física | `AGRESSAO_FISICA` |
| Agressão verbal | `AGRESSAO_VERBAL` |
| Atrapalhar deliberadamente a aula | `ATRAPALHAR_AULA` |
| Atraso (1ª aula, troca de professor ou após o recreio) | `ATRASO` |
| Cola em avaliação | `COLA_AVALIACAO` |
| Comportamento inadequado em redes sociais, no espaço escolar ou portando símbolos da instituição | `COMPORTAMENTO_REDES_SOCIAIS` |
| Conversas excessivas em sala | `CONVERSAS_EXCESSIVAS` |
| Dano ao patrimônio | `DANO_PATRIMONIO` |
| Desacato à autoridade escolar | `DESACATO_AUTORIDADE` |
| Desrespeito verbal a professores, colaboradores ou colegas | `DESRESPEITO_VERBAL` |
| Dever incompleto | `DEVER_INCOMPLETO` |
| Dormir em aula | `DORMIR_AULA` |
| Entrar em sala sem autorização | `ENTRAR_SEM_AUTORIZACAO` |
| Esquecer material didático | `ESQUECER_MATERIAL` |
| Intimidação sistemática | `INTIMIDACAO_SISTEMATICA` |
| Não entrega de tarefa | `NAO_ENTREGA_TAREFA` |
| Ser convidado a se retirar da sala por conversas excessivas ou atitudes inadequadas | `RETIRADO_DA_SALA` |
| Uniforme incompleto | `UNIFORME_INCOMPLETO` |
| Prejuízo intencional à organização da aula… | `PREJUIZO_ORGANIZACAO_AULA` |
| Uso indevido de celular | `USO_INDEVIDO_CELULAR` |

Os códigos são só uma sugestão — o essencial é o time (Mateus, Pedro, Francisco) fechar **uma** lista antes da integração. Para o dashboard, o front precisa traduzir código → texto.

## 4. Verificação

- As consultas SQL de histórico e indicadores foram executadas em um PostgreSQL 16 real, sobre as migrations V1 e V30, conferindo totais, agrupamentos por turma/tipo/mês/semana/dia, filtros de data inclusivos, ordenação e o caso sem dados.
- **Antes do primeiro PR:** rodar `.\mvnw.cmd test` (testes sem banco) e a suíte de integração com `TEST_DB_URL` (ver `BACKEND.md`). Se algum teste Java falhar por detalhe de API do Spring Boot 4, o ajuste é local ao arquivo de teste.
