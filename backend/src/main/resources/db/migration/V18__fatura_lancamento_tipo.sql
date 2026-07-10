-- Fatura paga é imutável: ajustes e estornos entram como lançamentos compensatórios
-- (valor pode ser negativo) na próxima fatura aberta.
ALTER TABLE fatura_lancamentos ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'COMPRA';
