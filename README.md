# Ethos — Frontend do Professor

Registro rápido de ocorrências escolares · Colégio Santa Maria Minas.

## Como rodar
Abra `index.html` no navegador (ou use a extensão Live Server do VS Code).

## Configuração (início do `app.js`)
- `API_URL`: endereço do backend Spring Boot.
- `USAR_MOCK_SE_FALHAR`: `true` usa dados fictícios se a API estiver fora do ar (apenas desenvolvimento). Coloque `false` em produção.
- `TIPOS`: valores (`v`) devem coincidir com o enum do backend.

## Endpoints consumidos
- `POST /auth/login`
- `GET /alunos?busca=&turma=`
- `POST /ocorrencias`

## LGPD
Todos os alunos do modo de demonstração são fictícios. Não usar dados reais sem autorização da escola.
