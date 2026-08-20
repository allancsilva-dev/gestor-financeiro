-- =============================================================================
-- V42 — Home de referencia: identificacao do cartao, foto de perfil e
--        notificacoes in-app
-- =============================================================================
-- Suporta os elementos da Home redesenhada que hoje nao tem origem no banco:
--   * cartao identificavel na lista de parcelas ("Visa .... 8034");
--   * avatar com foto real no cabecalho;
--   * sino com contagem de nao lidas.
--
-- Minimizacao de dados (LGPD): o cartao guarda SOMENTE os quatro ultimos
-- digitos, nunca o numero completo (PAN). A coluna e VARCHAR(4) com CHECK de
-- exatamente quatro digitos justamente para tornar impossivel armazenar o PAN.
--
-- Todas as colunas sao nullable: cartoes, usuarios e instalacoes existentes
-- seguem validos sem backfill.
-- =============================================================================

-- 1. Identificacao do cartao ---------------------------------------------------
ALTER TABLE contas ADD COLUMN ultimos_digitos VARCHAR(4);
ALTER TABLE contas ADD COLUMN bandeira VARCHAR(20);

ALTER TABLE contas ADD CONSTRAINT ck_contas_ultimos_digitos
    CHECK (ultimos_digitos IS NULL OR ultimos_digitos ~ '^[0-9]{4}$');

COMMENT ON COLUMN contas.ultimos_digitos IS
    'Quatro ultimos digitos do cartao. NUNCA o numero completo (LGPD).';
COMMENT ON COLUMN contas.bandeira IS
    'Bandeira declarada pelo usuario: VISA, MASTERCARD, ELO, AMEX, HIPERCARD, OUTRA.';

-- 2. Foto de perfil ------------------------------------------------------------
ALTER TABLE usuarios ADD COLUMN foto_url VARCHAR(500);

COMMENT ON COLUMN usuarios.foto_url IS
    'Caminho do avatar enviado pelo usuario. Nulo = fallback para iniciais.';

-- 3. Notificacoes in-app -------------------------------------------------------
CREATE TABLE notificacoes (
    id          BIGSERIAL    PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    tipo        VARCHAR(40)  NOT NULL,
    titulo      VARCHAR(120) NOT NULL,
    mensagem    VARCHAR(400) NOT NULL,
    destino     VARCHAR(40),
    destino_id  BIGINT,
    -- chave natural do evento: impede a mesma fatura/parcela gerar duplicata
    chave       VARCHAR(120) NOT NULL,
    lida        BOOLEAN      NOT NULL DEFAULT FALSE,
    criada_em   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- a geracao e idempotente: reprocessar o mesmo evento nao cria linha nova
CREATE UNIQUE INDEX ux_notificacoes_usuario_chave
    ON notificacoes (usuario_id, chave);

-- consulta da tela: nao lidas primeiro, mais recentes no topo
CREATE INDEX ix_notificacoes_usuario_lida_criada
    ON notificacoes (usuario_id, lida, criada_em DESC);
