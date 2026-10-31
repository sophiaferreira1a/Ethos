# Backend — guia rápido do time

Spring Boot 4.1.2 + Java 25 + PostgreSQL (Supabase). O backend é o único que fala com o banco.

## Rodar localmente

Defina as variáveis no terminal (ou na Run Configuration da IDE) e suba:

```powershell
$env:DB_URL="jdbc:postgresql://aws-1-sa-east-1.pooler.supabase.com:5432/postgres?sslmode=require"
$env:DB_USER="postgres.gzcrlxvhqgxrydnmxquu"
$env:DB_PASSWORD="pedir no grupo, nunca commitar"
.\mvnw.cmd spring-boot:run
```

`PORT` (default 8080), `CORS_ALLOWED_ORIGINS`, `SESSION_COOKIE_SAME_SITE` e `SESSION_COOKIE_SECURE` são opcionais em dev — ver `.env.example`.

## Usuários fictícios de desenvolvimento

Criados pela migration `V30__seed_usuarios_dev.sql`. Senha de todos: `Ocorrencia@2026`.

| E-mail | Perfil |
|---|---|
| coordenacao@escola.com | COORDENACAO |
| professor@escola.com | PROFESSOR |
| professor2@escola.com | PROFESSOR |

## Login (sessão por cookie)

```js
const resposta = await fetch("http://localhost:8080/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  credentials: "include", // obrigatório: mantém o cookie JSESSIONID
  body: JSON.stringify({ email: "professor@escola.com", senha: "Ocorrencia@2026" }),
});
// 200 -> { nome, email, role, redirectTo }
```

- `GET /me` devolve o usuário da sessão (ou 401).
- `POST /logout` encerra a sessão (204).
- `redirectTo` já vem com o destino conforme o perfil (`/professor` ou `/coordenacao`); ajuste em `application.properties` quando as rotas do frontend existirem.

Em produção (front na Vercel, API no Render) o cookie precisa de `SESSION_COOKIE_SAME_SITE=none` e `SESSION_COOKIE_SECURE=true`.

## Regras de acesso

| Método | Endpoint | Perfil |
|---|---|---|
| POST | /login | público |
| POST | /ocorrencias | PROFESSOR |
| GET | /ocorrencias, /ocorrencias/{id} | COORDENACAO |
| PUT/DELETE | /ocorrencias/{id} | PROFESSOR ou COORDENACAO (dono da ocorrência é checado no service) |
| PATCH | /ocorrencias/{id}/lancada | COORDENACAO |
| GET | /ocorrencias/{id}/historico | COORDENACAO |
| GET | /indicadores | COORDENACAO |

Tudo o que não está listado exige autenticação. Use `@PreAuthorize("hasRole('COORDENACAO')")` no service/controller quando precisar de regra extra.

## Usuário autenticado (para Mateus e Bernardo)

```java
@Service
public class OcorrenciaService {
    private final UsuarioLogado usuarioLogado;

    public OcorrenciaService(UsuarioLogado usuarioLogado) {
        this.usuarioLogado = usuarioLogado;
    }

    @Transactional
    public UUID criar(NovaOcorrencia dados) {
        UUID autor = usuarioLogado.id();   // também: email(), nome(), role(), isCoordenacao()
        // ... salva a ocorrência ...
        historicoService.registrar(ocorrencia.getId(), autor,
                AcaoHistorico.OCORRENCIA_CRIADA.name(), "criada pelo professor");
        return ocorrencia.getId();
    }
}
```

## Contrato de histórico

- Interface: `historico.HistoricoService.registrar(ocorrenciaId, usuarioId, acao, detalhes)`.
- Ações: `historico.AcaoHistorico` (`OCORRENCIA_CRIADA`, `OCORRENCIA_LANCADA`, `OCORRENCIA_ATUALIZADA`, `OCORRENCIA_EXCLUIDA`).
- A implementação real é `HistoricoServiceImpl` (o stub foi removido).
- **Precisa de transação.** `registrar` usa `Propagation.MANDATORY`: chame de dentro do `@Transactional` do seu service. Sem transação ele lança `IllegalTransactionStateException` em vez de gravar o histórico "solto" — assim a ocorrência e o histórico nunca ficam dessincronizados.
- Chame **depois** de salvar a ocorrência (o histórico tem FK para `ocorrencias`). Se a FK falhar, a exceção estoura no próprio `registrar` e desfaz a operação inteira.
- `acao` precisa ser o `name()` de uma constante de `AcaoHistorico`; qualquer outro valor lança `IllegalArgumentException`.
- `detalhes` é opcional (`null` ou em branco vira `null`).

## Histórico e indicadores (Bernardo)

Ambos exclusivos da **COORDENACAO** (filtro do `SecurityConfig` + `@PreAuthorize`).

### `GET /ocorrencias/{id}/historico`

Linha do tempo da ocorrência, da mais antiga para a mais recente. `404` se a ocorrência não existe; `400` se o id não é UUID.

```json
[
  { "id": "…", "acao": "OCORRENCIA_CRIADA", "detalhes": "criada pelo professor",
    "dataHora": "2026-10-07T12:00:00Z", "usuario": { "id": "…", "nome": "Maria Silva" } },
  { "id": "…", "acao": "OCORRENCIA_LANCADA", "detalhes": "lançada no sistema oficial",
    "dataHora": "2026-10-07T13:00:00Z", "usuario": { "id": "…", "nome": "Ana Coordenacao" } }
]
```

### `GET /indicadores`

Parâmetros opcionais (todos sobre a **data da ocorrência**):

| Parâmetro | Valores | Padrão |
|---|---|---|
| `de` | `yyyy-MM-dd` (inclusivo) | sem limite |
| `ate` | `yyyy-MM-dd` (inclusivo) | sem limite |
| `agrupamento` | `DIA`, `SEMANA` (rótulo = segunda-feira) ou `MES` | `MES` |

`400` para data fora do formato, `agrupamento` desconhecido ou `de` depois de `ate`.

```json
{
  "total": 4, "naoLancadas": 2, "lancadas": 2,
  "filtro": { "de": null, "ate": null, "agrupamento": "MES" },
  "porTurma":   [ { "turma": "2A", "total": 3, "naoLancadas": 2, "lancadas": 1 } ],
  "porTipo":    [ { "tipo": "COMPORTAMENTAL", "total": 2, "naoLancadas": 2, "lancadas": 0 } ],
  "porPeriodo": [ { "periodo": "2026-10", "total": 3, "naoLancadas": 2, "lancadas": 1 } ]
}
```

- `porTurma` e `porTipo`: mais ocorrências primeiro. `porPeriodo`: ordem cronológica.
- Período sem dados: totais `0` e listas vazias (nunca `404`).
- As consultas são só `SELECT` em SQL nativo (`IndicadoresRepository`); não usam o repository do Mateus.
- Propositalmente **não há** agrupamento por aluno nem ranking (ver seção 9 da documentação do projeto).

## Migrations

Flyway aplica `src/main/resources/db/migration` em ordem crescente de versão. Faixas reservadas: Mateus `V10+`, Bernardo `V20+`, Sophia `V30+`. Nunca edite uma migration já aplicada — crie outra.

## Testes

```powershell
.\mvnw.cmd test
```

Sem nenhuma configuração, roda tudo que **não** precisa de banco:

- `AutenticacaoEAutorizacaoTest` — acesso indevido (`@WebMvcTest`).
- `HistoricoEIndicadoresControllerTest` — contrato JSON, validação de parâmetros e permissões dos endpoints do Bernardo (`@WebMvcTest`).
- `HistoricoServiceImplTest`, `IndicadoresServiceTest` — regras dos services (Mockito).

`SistemaOcorrenciasApplicationTests` está `@Disabled` (exige banco real).

### Testes de integração (banco separado)

Os testes `*IntegracaoTest` sobem a aplicação completa (Spring Security + JPA + Flyway, login real com BCrypt) contra um PostgreSQL **separado** do Supabase. Eles só rodam se `TEST_DB_URL` estiver definida; sem ela aparecem como *skipped*.

```powershell
docker compose -f docker-compose.test.yml up -d      # PostgreSQL 16 em localhost:5433 (dados em memória)

$env:TEST_DB_URL="jdbc:postgresql://localhost:5433/ocorrencias_test"
$env:TEST_DB_USER="teste"
$env:TEST_DB_PASSWORD="teste"
.\mvnw.cmd test

docker compose -f docker-compose.test.yml down
```

> ⚠️ Esses testes **apagam** `ocorrencias`, `alunos`, `turmas` e `historicos` antes de cada teste. Por segurança eles se recusam a rodar se `TEST_DB_URL` contiver `supabase`. Nunca aponte para o banco de desenvolvimento compartilhado.

`FluxoProfessorCoordenacaoTest` (professor registra → coordenação lança → histórico e indicadores) está `@Disabled` até o Mateus integrar `POST /ocorrencias` e `PATCH /ocorrencias/{id}/lancada`; as instruções para habilitar estão no próprio arquivo.

## Deploy

Veja [`DEPLOY.md`](DEPLOY.md) (Render + Vercel + Supabase) e [`DECISOES_TECNICAS.md`](DECISOES_TECNICAS.md) (decisões e problemas de integração).
