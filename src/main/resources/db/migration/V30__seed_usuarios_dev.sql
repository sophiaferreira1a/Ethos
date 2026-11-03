-- Dados FICTICIOS de desenvolvimento (nunca usar em producao).
-- Senha de todos os usuarios de teste: Ocorrencia@2026
-- Trocar/remover quando a escola liberar dados reais.
--
-- Hashes BCrypt (strength 10) gerados para a senha acima; a senha em texto puro
-- nao existe no banco nem no repositorio.

insert into usuarios (nome, email, senha_hash, role) values
  ('Ana Coordenacao', 'coordenacao@escola.com', '$2a$10$QrL7cD3s8FuohYQzFKYIeOSdsTBaDPHPj3yFpF2hKKJjRq5eOnk4e', 'COORDENACAO'),
  ('Maria Silva', 'professor@escola.com', '$2a$10$JR4kqQgJWBKr1pPSrzj6nesED1nQIWeLz5nenme78wk/b1rVMKCa2', 'PROFESSOR'),
  ('Joao Pereira', 'professor2@escola.com', '$2a$10$NKOi7uJXrenEIZ8kPb5nRO4QWpulqOpM8zI0AOIsERXlj4UsERU.G', 'PROFESSOR')
on conflict (email) do nothing;
