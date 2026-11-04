# Deploy — Supabase + Render + Vercel

```
Navegador ──► Vercel (frontend estático) ──/api (rewrite)──► Render (Spring Boot, Docker) ──JDBC──► Supabase (PostgreSQL)
```

| Peça | Onde | Arquivo |
|---|---|---|
| Banco | Supabase (região São Paulo, **Session pooler** porta 5432) | `db/migration/*` (Flyway roda no boot do backend) |
| Backend | Render, via Docker (Java 25) | `Dockerfile`, `.dockerignore`, `render.yaml` |
| Frontend | Vercel, site estático com proxy `/api` | `vercel.json`, `.vercelignore` |

## Variáveis de ambiente (Render)

Nada disto vai para o GitHub. No Render: *Environment* do serviço (ou o prompt do Blueprint para as `sync: false`).

| Variável | Valor | Observação |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://<host-do-pooler>:5432/postgres?sslmode=require` | Supabase → Connect → **Session pooler**. Não use a conexão direta (IPv6) nem a porta 6543. |
| `DB_USER` | `postgres.<project-ref>` | |
| `DB_PASSWORD` | senha do banco | |
| `CORS_ALLOWED_ORIGINS` | `https://<seu-front>.vercel.app` | Configure mesmo com o proxy: a Vercel pode repassar o header `Origin` ao Render. Origem exata, **sem barra no final**. Várias: separadas por vírgula. |
| `SESSION_COOKIE_SAME_SITE` | `lax` | Com o proxy `/api`, navegador e API ficam no mesmo domínio. Já vem no `render.yaml`. |
| `SESSION_COOKIE_SECURE` | `true` | Obrigatório em produção HTTPS. Já vem no `render.yaml`. |
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

> Se o nome `ethos-api` já estiver em uso no Render, o serviço receberá outro subdomínio. Copie a URL final e ajuste o `destination` do rewrite em `vercel.json`.

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
4. Em `vercel.json`, ajuste o `destination` do rewrite `/api/:path*` para a URL do seu serviço Render (o exemplo usa `https://ethos-api.onrender.com`). O `app.js` e o `coordenacao/api.js` já usam `/api` automaticamente fora do localhost.
5. Use o domínio gratuito `https://<projeto>.vercel.app`. **Não use `ethos.com`**: esse domínio pertence a outra empresa e não pode ser verificado por este projeto.
6. Copie a URL final da Vercel para `CORS_ALLOWED_ORIGINS` no Render (a Vercel pode repassar o header `Origin` ao backend).

> Se o front e o back forem para repositórios/pastas separados, ajuste o *Root Directory* da Vercel e remova o `.vercelignore`.

### Sessão via proxy (padrão)

O login usa cookie `JSESSIONID`. O front na Vercel chama `/api`, e a Vercel repassa para o Render. Para o navegador, tudo é o mesmo domínio (`*.vercel.app`), então o cookie não é bloqueado como cookie de terceiros. O `app.js` e o `coordenacao/api.js` já detectam produção e usam `/api`.

Configuração correspondente no Render:

- `SESSION_COOKIE_SAME_SITE=lax`
- `SESSION_COOKIE_SECURE=true`
- `CORS_ALLOWED_ORIGINS` com a URL da Vercel (a Vercel pode repassar o `Origin` ao Render).

O `fetch` do front continua usando `credentials: "include"`, o que é inofensivo no modo proxy.

### Modo direto (alternativa, sem proxy)

Se em algum momento o front chamar o Render diretamente (`https://<servico>.onrender.com`), será um cookie de terceiros. Nesse caso:

- `SESSION_COOKIE_SAME_SITE=none`
- `SESSION_COOKIE_SECURE=true`
- `CORS_ALLOWED_ORIGINS` com a URL exata da Vercel
- remover/ignorar o rewrite de `/api` e apontar `API_URL` para o Render no código.

Alguns navegadores (Safari/iOS, Chrome em modo restrito) podem bloquear cookies de terceiros mesmo com essa configuração; por isso o proxy é o caminho recomendado.

## Antes de cada apresentação

- [ ] Supabase: projeto ativo (o plano gratuito pausa por inatividade, ~1 semana). Abra o painel e confira.
- [ ] Render: o plano gratuito "dorme". **Acorde o serviço 2–3 min antes** abrindo a URL direta do Render (`https://<servico>.onrender.com/indicadores`), não pelo proxy da Vercel (a primeira resposta demora ~30–60 s e pode estourar o timeout do proxy).
- [ ] Login de teste com `coordenacao@escola.com` e `professor@escola.com`.
- [ ] O rewrite `/api/:path*` de `vercel.json` aponta para o serviço Render correto.
- [ ] `CORS_ALLOWED_ORIGINS` contém a URL exata da Vercel (sem `/` no final).
- [ ] `SESSION_COOKIE_SAME_SITE=lax` e `SESSION_COOKIE_SECURE=true` no Render.
- [ ] Só dados fictícios no banco.
- [ ] Plano gratuito do Supabase **não tem backup automático**: migrations e dados de exemplo ficam no repositório.

## Problemas comuns

| Sintoma | Causa provável |
|---|---|
| Deploy falha com `Connection ... refused` / `UnknownHost` no Flyway | `DB_URL` errada ou usando conexão direta (IPv6). Use o **Session pooler** porta 5432. |
| `FATAL: Tenant or user not found` | `DB_USER` sem o sufixo `.<project-ref>`. |
| `prepared statement ... already exists` | Está na porta **6543** (Transaction pooler). Troque para 5432. |
| Navegador: erro de CORS | Você está chamando o Render diretamente e `CORS_ALLOWED_ORIGINS` não bate exatamente com a origem (esquema, domínio, sem `/` final). Com o proxy `/api`, esse erro não deve aparecer. |
| `/api/...` retorna 404 na Vercel | O `destination` do rewrite em `vercel.json` está com a URL errada do Render, ou o serviço ainda está dormindo/subindo. |
| Login 200 mas próximas chamadas 401 | Cookie bloqueado: falta `credentials: "include"` no front, `SameSite`/`Secure` incorretos, ou você está no modo direto e o navegador bloqueia cookie de terceiros (use o proxy). |
| Render reinicia em loop / `OutOfMemoryError` | Plano gratuito tem 512 MB; ajuste `JAVA_OPTS` (ex.: `-XX:MaxRAMPercentage=60`). |
| Primeira requisição muito lenta | Serviço dormindo (plano gratuito). |
