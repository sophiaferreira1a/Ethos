# Ethos

Registro rápido de ocorrências escolares · Colégio Santa Maria Minas.

## Como rodar
Abra `index.html` no navegador (ou use a extensão Live Server do VS Code).

## Endpoints consumidos

### Professor
- `POST /auth/login` (ou `/login`, conforme o ambiente)
- `GET /alunos?busca=&turma=`
- `GET /turmas`
- `POST /ocorrencias/lote` — uma ocorrência por aluno selecionado

### Coordenação
- `GET/PUT/DELETE /ocorrencias`
- `PATCH /ocorrencias/{id}/lancada`
- `GET/POST/PUT/DELETE /alunos`
- `PATCH /alunos/turma`
- `GET/POST/PUT/DELETE /turmas`

## Seleção múltipla no formulário do professor
No passo 1, o professor escolhe a turma e marca um ou mais alunos. Ao enviar, cada aluno selecionado gera uma ocorrência própria, gravada na mesma transação (`POST /ocorrencias/lote`). Se qualquer aluno estiver inválido, nada é salvo.

## LGPD
Todos os alunos do modo de demonstração são fictícios. Não usar dados reais sem autorização da escola.
