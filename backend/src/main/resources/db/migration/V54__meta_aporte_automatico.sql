-- Aporte automático de meta. Opt-in explícito: o app não passa a mover dinheiro do titular porque
-- ele preencheu um valor mensal — precisa dizer "sim, faça isso todo mês, desta conta".
ALTER TABLE metas ADD COLUMN IF NOT EXISTS aporte_automatico BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE metas ADD COLUMN IF NOT EXISTS aporte_dia SMALLINT;
ALTER TABLE metas ADD COLUMN IF NOT EXISTS aporte_carteira_id BIGINT;

ALTER TABLE metas
    ADD CONSTRAINT fk_metas_aporte_carteira FOREIGN KEY (aporte_carteira_id) REFERENCES carteiras (id);

-- Ligar o aporte sem dizer quanto, quando e de onde deixaria o job sem instrução.
ALTER TABLE metas
    ADD CONSTRAINT ck_metas_aporte_automatico CHECK (
        aporte_automatico = FALSE
        OR (valor_mensal IS NOT NULL AND valor_mensal > 0
            AND aporte_dia BETWEEN 1 AND 28
            AND aporte_carteira_id IS NOT NULL)
    );

-- Marca da última competência aportada. A chave de idempotência do ledger só protege a meta com
-- cofre real; a reserva virtual não gera lançamento, então a trava do aporte mora aqui e vale para
-- as duas modalidades.
ALTER TABLE metas ADD COLUMN IF NOT EXISTS aporte_ultima_competencia VARCHAR(7);

ALTER TABLE metas
    ADD CONSTRAINT ck_metas_aporte_competencia CHECK (
        aporte_ultima_competencia IS NULL OR aporte_ultima_competencia ~ '^[0-9]{4}-[0-9]{2}$'
    );

COMMENT ON COLUMN metas.aporte_dia IS
    'Dia do mês do aporte, limitado a 28 para existir em todo mês.';
