-- Cancelamento logico. historicos.ocorrencia_id referencia ocorrencias(id)
-- sem ON DELETE, e o historico nao pode ser apagado. A linha permanece;
-- cancelada_em preenchido tira a ocorrencia das listagens.

alter table ocorrencias add column cancelada_em timestamptz;
