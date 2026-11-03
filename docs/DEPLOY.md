# Deploy — Supabase + Render + Vercel

```
Navegador ──► Vercel (frontend estático) ──fetch──► Render (Spring Boot, Docker) ──JDBC──► Supabase (PostgreSQL)
```

| Peça | Onde | Arquivo |
|---|---|---|
| Banco | Supabase (região São Paulo, **Session pooler** porta 5432) | `db/migration/*` (Flyway roda no boot do backend) |
| Backend | Render, via Docker (Java 25) | `Dockerfile`, `.dockerignore`, `render.yaml` |
| Frontend | Vercel, site estático | `vercel.json`, `.vercelignore` |

## Variáveis de ambiente (Render)

Nada disto vai para o GitHub. No Render: *Environment* do serviço (ou o prompt do Blueprint para as `sync: false`).

| Variável | Valor | Observação |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://<host-do-pooler>:5432/postgres?sslmode=require` | Supabase → Connect → **Session pooler**. Não use a conexão direta (IPv6) nem a porta 6543. |
| `DB_USER` | `postgres.<project-ref>` | |
| `DB_PASSWORD` | senha do banco | |
| `CORS_ALLOWED_ORIGINS` | `https://<seu-front>.vercel.app` | Origem exata, **sem barra no final**. Várias: separadas por vírgula. |
| `SESSION_COOKIE_SAME_SITE` | `none` | Front e API em domínios diferentes. Já vem no `render.yaml`. |
| `SESSION_COOKIE_SECURE` | `true` | Obrigatório com `SameSite=None`. Já vem no `render.yaml`. |
| `PORT` | — | O Render injeta sozinho. |
| `JAVA_OPTS` | opcional | Padrão da imagem: `-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError`. |
| `REDIRECT_PROFESSOR` / `REDIRECT_COORDENACAO` | opcional | Rotas pós-login (defaults em `application.properties`). |

`.env.example` continua sendo o modelo para rodar na máquina local.

## 1. Supabase

1. Projeto criado na região **América do Sul (São Paulo)**.
2. Não precisa rodar SQL à mão: a V1 e as demais migrations são aplicadas pelo Flyway quando o backend sobe.
3. RLS fica ligado nas tabelas (já está na V1). O Spring conecta com o usuário do banco, que ignora o RLS.
4. **A V30 (seed) cria usuários de teste com senha conhecida.** Serve para a apresentação com dados fictícios; **remova antes de usar dados reais** (crie uma migration nova que apague esses usuários — nunca edite a V30).

## 2. Render (backend)

**Com Blueprint (recomendado):** Render → *New* → *Blueprint* → escolha o repositório → o `render.yaml` cria o serviço `ethos-api` → preencha `DB_URL`, `DB_USER`, `DB_PASSWORD` e `CORS_ALLOWED_ORIGINS`.

**Manual:** *New* → *Web Service* → *Language: Docker* → branch `main` → mesmas variáveis da tabela acima.

Primeiro build leva alguns minutos (baixa Maven e dependências). Depois, cada push na `main` faz novo deploy.

Teste: `https://<seu-servico>.onrender.com/indicadores` deve responder **401** `{"status":401,"erro":"Autenticacao necessaria"}` — isso prova que o backend subiu, conectou no banco, rodou o Flyway e a segurança está ativa.

### Testar a imagem na máquina antes de subir

```powershell
docker build -t ethos-api .
docker run --rm -p 8080:8080 `
  -e DB_URL="jdbc:postgresql://...:5432/postgres?sslmode=require" `
  -e DB_USER="postgres.<project-ref>" -e DB_PASSWORD="<senha>" `
  ethos-api
```

## 3. Vercel (frontend)

1. *Add New Project* → importe o repositório.
2. *Framework Preset*: **Other**. *Build Command* e *Output Directory*: deixe em branco (site estático na raiz).
3. O `.vercelignore` deixa o backend Java de fora do upload.
4. Em `app.js`, troque `API_URL` pela URL do Render (`https://ethos-api.onrender.com`). Depois copie a URL final da Vercel para `CORS_ALLOWED_ORIGINS` no Render.

> Se o front e o back forem para repositórios/pastas separados, ajuste o *Root Directory* da Vercel e remova o `.vercelignore`.

### Cookie de sessão entre domínios (atenção)

O login usa cookie `JSESSIONID`. Com front em `*.vercel.app` e API em `*.onrender.com`, é um cookie **de terceiros**: precisa de `SameSite=None; Secure` (já configurado) **e** de `credentials: "include"` em todo `fetch` do front. Alguns navegadores (Safari/iOS, Chrome em modo restrito) bloqueiam cookie de terceiros mesmo assim.

Se o login "funcionar mas a sessão não persistir" em algum aparelho, a saída robusta é fazer o navegador falar só com a Vercel e deixar a Vercel repassar para o Render (cookie passa a ser do mesmo domínio). Em `vercel.json`:

```json
"rewrites": [
  { "source": "/api/:path*", "destination": "https://<seu-servico>.onrender.com/:path*" }
]
```

e o front passa a usar `API_URL = "/api"`. Nesse modo o CORS deixa de ser necessário e `SESSION_COOKIE_SAME_SITE` pode voltar para `lax`. É uma decisão do time (muda o `API_URL` do Pedro e do Francisco); por isso **não** está ligado por padrão.

## Antes de cada apresentação

- [ ] Supabase: projeto ativo (o plano gratuito pausa por inatividade, ~1 semana). Abra o painel e confira.
- [ ] Render: o plano gratuito "dorme". **Acorde o serviço 2–3 min antes** abrindo `/indicadores` (a primeira resposta demora ~30–60 s).
- [ ] Login de teste com `coordenacao@escola.com` e `professor@escola.com`.
- [ ] `CORS_ALLOWED_ORIGINS` contém a URL exata da Vercel (inclusive se houver domínio de preview/produção diferentes).
- [ ] Só dados fictícios no banco.
- [ ] Plano gratuito do Supabase **não tem backup automático**: migrations e dados de exemplo ficam no repositório.

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| Deploy falha com `Connection ... refused` / `UnknownHost` no Flyway | `DB_URL` errada ou usando conexão direta (IPv6). Use o **Session pooler** porta 5432. |
| `FATAL: Tenant or user not found` | `DB_USER` sem o sufixo `.<project-ref>`. |
| `prepared statement ... already exists` | Está na porta **6543** (Transaction pooler). Troque para 5432. |
| Navegador: erro de CORS | `CORS_ALLOWED_ORIGINS` não bate exatamente com a origem (esquema, domínio, sem `/` final). |
| Login 200 mas próximas chamadas 401 | Cookie bloqueado: falta `credentials: "include"` no front, ou `SameSite`/`Secure` incorretos, ou navegador bloqueando cookie de terceiros (ver proxy acima). |
| Render reinicia em loop / `OutOfMemoryError` | Plano gratuito tem 512 MB; ajuste `JAVA_OPTS` (ex.: `-XX:MaxRAMPercentage=60`). |
| Primeira requisição muito lenta | Serviço dormindo (plano gratuito). |
