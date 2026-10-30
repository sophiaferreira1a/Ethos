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
- Hoje existe apenas `HistoricoServiceStub` (loga e não grava). **Bernardo:** ao implementar a versão real, apague o stub — duas implementações quebram o contexto do Spring.
- Chame dentro da mesma `@Transactional` da operação: se o histórico falhar, a operação é desfeita.

## Migrations

Flyway aplica `src/main/resources/db/migration` em ordem crescente de versão. Faixas reservadas: Mateus `V10+`, Bernardo `V20+`, Sophia `V30+`. Nunca edite uma migration já aplicada — crie outra.

## Testes

```powershell
.\mvnw.cmd test
```

`AutenticacaoEAutorizacaoTest` cobre as tentativas de acesso indevido usando `@WebMvcTest` (não precisa de banco). `SistemaOcorrenciasApplicationTests` está `@Disabled` porque sobe o contexto completo e exige banco real.
