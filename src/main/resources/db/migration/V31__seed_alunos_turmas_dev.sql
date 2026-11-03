-- Turmas e alunos FICTICIOS para desenvolvimento (LGPD: nenhum dado real).
-- Mesmos nomes da previa do professor (app.js). Pode rodar sobre dados ja existentes:
-- nao duplica turma (nome unico) nem aluno com o mesmo nome na mesma turma.

insert into turmas (nome) values
  ('6A'),
  ('6B'),
  ('7A'),
  ('7B'),
  ('8A'),
  ('8B'),
  ('9A'),
  ('9B'),
  ('1EM'),
  ('2EM'),
  ('3EM')
on conflict (nome) do nothing;

insert into alunos (nome, turma_id)
select v.nome, t.id
from (values
  ('Ana Clara Souza', '6A'),
  ('Bruno Henrique Lima', '6A'),
  ('Camila Rocha', '6A'),
  ('Davi Nogueira', '6A'),
  ('Carla Mendes', '6B'),
  ('Enzo Gabriel Costa', '6B'),
  ('Helena Barros', '6B'),
  ('Igor Teixeira', '6B'),
  ('Diego Alves', '7A'),
  ('Eduarda Pires', '7A'),
  ('Julia Fernandes', '7A'),
  ('Lucas Martins', '7A'),
  ('Mariana Duarte', '7B'),
  ('Nicolas Ribeiro', '7B'),
  ('Olivia Cardoso', '7B'),
  ('Pedro Lucas Gomes', '7B'),
  ('Felipe Ramos', '8A'),
  ('Larissa Freitas', '8A'),
  ('Matheus Araújo', '8A'),
  ('Natália Campos', '8A'),
  ('Rafael Monteiro', '8B'),
  ('Sofia Carvalho', '8B'),
  ('Thiago Moreira', '8B'),
  ('Valentina Neves', '8B'),
  ('Gabriela Torres', '9A'),
  ('Henrique Dias', '9A'),
  ('Isabela Moraes', '9A'),
  ('João Vitor Pinto', '9A'),
  ('Karen Batista', '9B'),
  ('Leonardo Siqueira', '9B'),
  ('Manuela Andrade', '9B'),
  ('Otávio Lacerda', '9B'),
  ('Paula Vieira', '1EM'),
  ('Renato Cunha', '1EM'),
  ('Sara Medeiros', '1EM'),
  ('Tiago Brandão', '1EM'),
  ('Úrsula Pacheco', '2EM'),
  ('Vinícius Falcão', '2EM'),
  ('Yasmin Teles', '2EM'),
  ('Arthur Bastos', '2EM'),
  ('Beatriz Lopes', '3EM'),
  ('Caio Veloso', '3EM'),
  ('Daniela Rezende', '3EM'),
  ('Eric Salgado', '3EM')
) as v(nome, turma)
join turmas t on t.nome = v.turma
where not exists (
  select 1 from alunos a where a.nome = v.nome and a.turma_id = t.id
);
