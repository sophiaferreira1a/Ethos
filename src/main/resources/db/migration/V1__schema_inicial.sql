-- Schema inicial do Sistema de Ocorrencias Escolares.
-- Aplicado uma unica vez pelo Flyway. Nunca editar este arquivo depois de aplicado:
-- qualquer alteracao futura deve ser uma nova migration (V30+ para Sophia).

create table usuarios (
  id uuid primary key default gen_random_uuid(),
  nome varchar(150) not null,
  email varchar(150) not null unique,
  senha_hash varchar(100) not null,
  role varchar(20) not null check (role in ('PROFESSOR','COORDENACAO')),
  ativo boolean not null default true,
  criado_em timestamptz not null default now()
);

create table turmas (
  id uuid primary key default gen_random_uuid(),
  nome varchar(20) not null unique
);

create table alunos (
  id uuid primary key default gen_random_uuid(),
  nome varchar(150) not null,
  turma_id uuid not null references turmas(id)
);

create table ocorrencias (
  id uuid primary key default gen_random_uuid(),
  aluno_id uuid not null references alunos(id),
  turma_id uuid not null references turmas(id),
  professor_id uuid not null references usuarios(id),
  tipo varchar(30) not null,
  descricao text not null,
  medidas_tomadas text,
  data date not null,
  status_lancamento varchar(20) not null default 'NAO_LANCADA'
    check (status_lancamento in ('NAO_LANCADA','LANCADA')),
  data_lancamento timestamptz,
  responsavel_lancamento_id uuid references usuarios(id),
  criado_em timestamptz not null default now(),
  constraint ck_lancamento check (
    (status_lancamento = 'NAO_LANCADA'
      and data_lancamento is null
      and responsavel_lancamento_id is null)
    or
    (status_lancamento = 'LANCADA'
      and data_lancamento is not null
      and responsavel_lancamento_id is not null)
  )
);

create table historicos (
  id uuid primary key default gen_random_uuid(),
  ocorrencia_id uuid not null references ocorrencias(id),
  usuario_id uuid not null references usuarios(id),
  acao varchar(40) not null,
  detalhes text,
  data_hora timestamptz not null default now()
);

create index idx_ocorrencias_status on ocorrencias(status_lancamento);
create index idx_ocorrencias_turma on ocorrencias(turma_id);
create index idx_ocorrencias_data on ocorrencias(data);
create index idx_ocorrencias_aluno on ocorrencias(aluno_id);
create index idx_historicos_ocorr on historicos(ocorrencia_id);

-- RLS habilitado sem policies: a API publica do Supabase nao devolve nada.
-- O backend Spring conecta como owner do banco, que ignora RLS.
alter table usuarios enable row level security;
alter table turmas enable row level security;
alter table alunos enable row level security;
alter table ocorrencias enable row level security;
alter table historicos enable row level security;
