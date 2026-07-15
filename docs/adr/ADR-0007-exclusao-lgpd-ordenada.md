# ADR-0007 — Exclusao LGPD por manifesto ordenado app-level

- **Status:** Accepted (2026-07-15)
- **Contexto:** `UsuarioExclusaoService.DELETES_ORDENADOS` nao inclui `ExecucaoRecorrencia`
  (tabela criada na V29 com FKs restritivas para usuario, conta fixa e transacao, sem cascade).
  Usuario com recorrencias executadas nao consegue excluir a conta (P0-2). Causa raiz sistemica:
  cada tabela nova pode ser esquecida da lista.
- **Decisao:** Exclusao do titular permanece **app-level, hard-delete, ordenada** (auditavel, sem
  `ON DELETE CASCADE` no schema). A lista vira um **manifesto** explicito (tabela + JPQL) que passa
  a ser a fonte unica da ordem de exclusao. Um teste-guardiao em PostgreSQL real consulta o
  catalogo de FKs e compara todas as tabelas alcancaveis a partir do titular com o manifesto;
  tabela fora do manifesto (e sem cascade explicitamente aceito) falha o build.
- **Consequencias:** `ExecucaoRecorrencia` entra no manifesto antes de `Transacao` e `ContaFixa`.
  Teste de integracao cobre grafo completo (recorrencia realizada, pulada e com falha de saldo),
  remocao de arquivos do titular, isolamento entre usuarios e rollback. Toda migration futura que
  criar tabela ligada ao titular obriga atualizacao do manifesto — garantido pelo guardiao.
