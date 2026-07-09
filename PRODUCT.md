# Product

## Users

Público geral brasileiro controlando finanças pessoais no dia a dia: salário, mercado, contas fixas, cartão de crédito, metas de poupança. Usam primariamente o celular, em momentos curtos (conferir saldo na fila, lançar um gasto ao sair do mercado). Não são especialistas em finanças — precisam de clareza imediata, não de planilhas.

Superfície prioritária: **app mobile (React Native + Expo em `mobile/`)**. O frontend web (`frontend/`) será alinhado depois que o mobile estiver pronto.

## Product Purpose

Gestor Financeiro pessoal completo: carteiras, transações, cartões/faturas, contas fixas, orçamentos por categoria, metas e relatórios, com backend próprio (Spring Boot + PostgreSQL). Sucesso = o usuário abre o app, entende sua situação financeira em segundos e lança um gasto em menos de 10 segundos.

## Brand Personality

Leve, amigável, confiável. Finanças sem cara de banco: tom acolhedor ("Olá, Mariana 👋"), emoji como ícones de categoria em quadradinhos pastel, números grandes e claros. Dinheiro que entra é verde, que sai é vermelho — sempre. A referência visual canônica é o protótipo em `docs/Gestor Financeiro (standalone).html` (tema claro lavanda + violeta).

## Anti-references

- Internet banking corporativo: telas densas, jargão, cinza sobre cinza.
- O visual antigo do frontend web: dark slate + gradiente laranja + ícones arco-íris (um por item de menu) — sem identidade única.
- Dashboards SaaS genéricos: hero-metric com gradiente, cards idênticos em grade infinita.

## Design Principles

1. **Saldo em 3 segundos** — cada tela responde primeiro à pergunta principal (quanto tenho? quanto gastei?) com número grande; detalhe vem depois.
2. **Lançar gasto sem fricção** — o "+" central é a ação primária do app inteiro; formulários curtos, teclado certo, valor primeiro.
3. **Verde entra, vermelho sai** — semântica de cor de dinheiro é sagrada e consistente em todas as telas; violeta é marca/navegação, nunca valor.
4. **Emoji é o sistema de ícones de categoria** — pastel tile + emoji, não biblioteca de ícones colorida.
5. **Estados nunca ficam mudos** — loading (skeleton), vazio (convite à ação) e erro (retry) desenhados em toda lista/tela.

## Accessibility & Inclusion

WCAG AA: contraste de texto ≥4.5:1 (atenção ao muted `#aab0bd`, que falha sobre branco — usar só em texto decorativo grande ou escurecer), alvos de toque ≥44pt, labels de acessibilidade em ícones e botões, suporte a `prefers-reduced-motion`/`Reduce Motion` do iOS, dark mode como cidadão de primeira classe (toggle claro/escuro).
