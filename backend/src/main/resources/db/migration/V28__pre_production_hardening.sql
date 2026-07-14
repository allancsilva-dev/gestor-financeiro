-- Hardening pré-produção (P0/P1).
-- 1) Optimistic locking em parcelas: evita débito duplicado na carteira sob pagamento
--    concorrente (marcarComoPaga). Coerente com o padrão de V2 (carteiras/contas/metas/categorias).
ALTER TABLE parcelas ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 NOT NULL;

-- 2) Índice de suporte à FK movimentos_carteira.carteira_id. O único índice existente
--    (V11) é composto liderado por usuario_id, inútil para filtro só por carteira_id —
--    usado na checagem de exclusão de carteira (existsByCarteiraId) e na validação de FK.
CREATE INDEX IF NOT EXISTS idx_movimentos_carteira_carteira ON movimentos_carteira(carteira_id);

-- 3) Índice em refresh_tokens.usuario_id: hot path de auth (findByUsuario, revokeAllByUsuario,
--    countValidTokensByUsuario) executado em todo login/refresh/logout-all, hoje em full scan.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_usuario ON refresh_tokens(usuario_id);
