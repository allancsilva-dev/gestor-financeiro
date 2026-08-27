-- Aparelhos que aceitam receber aviso. Token do Expo é credencial de entrega: identifica um
-- aparelho, então vale globalmente e migra de titular quando outra pessoa entra no mesmo aparelho.
CREATE TABLE notificacao_dispositivos (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT NOT NULL REFERENCES usuarios (id),
    push_token     VARCHAR(200) NOT NULL,
    plataforma     VARCHAR(10) NOT NULL,
    ativo          BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ux_notificacao_dispositivos_token UNIQUE (push_token),
    CONSTRAINT ck_notificacao_dispositivos_plataforma CHECK (plataforma IN ('IOS', 'ANDROID')),
    -- Formato do Expo. Token fora do formato não é entregável e não deve nem entrar.
    CONSTRAINT ck_notificacao_dispositivos_token CHECK (
        push_token ~ '^Expo(nent)?PushToken\[[A-Za-z0-9._%+-]{1,150}\]$'
    )
);

CREATE INDEX ix_notificacao_dispositivos_usuario
    ON notificacao_dispositivos (usuario_id)
    WHERE ativo;

COMMENT ON TABLE notificacao_dispositivos IS
    'Aparelhos registrados para push. Sai no manifesto de exclusão LGPD junto com o titular.';
