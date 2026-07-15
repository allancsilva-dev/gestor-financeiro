# ADR-0003 — Timezone de negocio e `Clock` injetavel

- **Status:** Accepted (2026-07-15)
- **Contexto:** Recorrencias fixam `America/Sao_Paulo` (`RecorrenciaScheduler`,
  `ContaFixaService`), enquanto dashboard, faturas, orcamento, relatorios e anexos usam
  `LocalDate.now()`/`LocalDateTime.now()`/`YearMonth.now()` no timezone default da JVM (P1-5).
  Em servidor UTC, dia e mes viram adiantados e os numeros divergem entre scheduler e APIs.
- **Decisao:** Timezone de negocio unico por configuracao: propriedade `app.business.timezone`
  (default `America/Sao_Paulo`) alimenta um bean `java.time.Clock` injetado em todos os servicos
  financeiros. Proibido `now()` sem `Clock` em servico financeiro; um teste-guardiao varre as
  fontes e falha na violacao (excecoes nao financeiras sao documentadas no proprio teste).
  O scheduler usa a mesma propriedade no atributo `zone`. Multi-timezone por usuario e nao-objetivo
  nesta fase.
- **Consequencias:** Testes temporais usam `Clock.fixed` cobrindo viradas de dia, mes e ano entre
  UTC e Sao Paulo. Persistencia integral em UTC (hoje impedida por colunas `TIMESTAMP` sem zona)
  fica registrada para a Fase 2; este ADR fecha apenas o calculo consistente de datas de negocio.
