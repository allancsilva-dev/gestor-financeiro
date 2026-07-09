-- Impede categorias duplicadas por usuário (case-insensitive), apenas entre ativas.
-- Parcial em ativo = TRUE para permitir recriar categoria com nome de uma desativada.
CREATE UNIQUE INDEX idx_categorias_usuario_nome_ativo
    ON categorias (usuario_id, lower(nome))
    WHERE ativo = TRUE;
