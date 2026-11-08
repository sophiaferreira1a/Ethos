-- Exclusao logica de alunos.
-- O aluno deixa de aparecer nas listas e nao pode ser usado em novas ocorrencias,
-- mas ocorrencias e historicos antigos continuam preservados.

alter table alunos add column excluido_em timestamptz;

create index idx_alunos_ativos on alunos (turma_id) where excluido_em is null;
