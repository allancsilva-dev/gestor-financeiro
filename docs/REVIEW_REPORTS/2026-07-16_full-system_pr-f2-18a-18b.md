# PR-F2-18A/18B — Contratos canônicos e migração dos clientes

## Resultado

Implementados os contratos canônicos finais de contas financeiras e cartões e migrados web e
mobile para esses contratos. Nenhum deploy, migration destrutiva ou aplicação do stash ocorreu.

## Evidências

- Backend: 242 testes, zero falha/erro; inclui testes de contrato, ownership, paginação,
  criação pareada e PostgreSQL real/Testcontainers.
- Frontend: build de produção e 35 testes; lint sem erros.
- Mobile: typecheck e lint sem erros; 17 testes; Expo Doctor 18/18.
- Dependências: npm frontend sem vulnerabilidades; mobile sem alta/crítica (15 moderadas e uma
  baixa); relatório OWASP de 2026-07-16 sem severidade alta/crítica.
- Busca estática: nenhum serviço ou chamada HTTP de cliente para `/contas` ou `/carteiras`.
- `git diff --check`: sem erros.

## Gate PROB-0081

Bloqueado corretamente antes do PR-F2-19. A máquina não possui `rclone`, remote configurado,
`deploy/backup/rclone/rclone.conf`, `deploy/backup/backup-public.asc` ou variáveis de backup.
Logo não é possível comprovar cópia realmente off-host, `rclone check --download`, checksum
remoto, restore PostgreSQL 17 e conteúdo integral de anexo. O status permanece REABERTO.

## Decisão

PR-F2-18A/18B estão prontos para revisão. PR-F2-19 e PR-F2-20 permanecem proibidos até o drill
off-host real ser executado e registrado pelo coordenador host-side.
