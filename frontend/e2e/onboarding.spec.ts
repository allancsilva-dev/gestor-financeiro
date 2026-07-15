import { test, expect } from '@playwright/test';

// Jornada principal (ADR-0002 / PROB-0078): cadastro → onboarding completo → dashboard.
// O onboarding web deve persistir tudo em UMA chamada ao endpoint canônico /finalizar.
test('cadastro, onboarding atômico e dashboard', async ({ page }) => {
  const email = `e2e-${Date.now()}@teste.com`;
  const senha = 'SenhaForte123';

  // Cadastro
  await page.goto('/register');
  await page.getByPlaceholder('Seu nome').fill('Usuária E2E');
  await page.getByPlaceholder('seu@email.com').fill(email);
  await page.getByPlaceholder('Mínimo 6 caracteres').fill(senha);
  await page.getByPlaceholder('Digite a senha novamente').fill(senha);
  await page.getByRole('checkbox').check();
  await page.getByRole('button', { name: /criar conta/i }).click();

  // Login
  await page.waitForURL('**/login');
  await expect(page.getByRole('heading', { name: 'Entrar na sua conta' })).toBeVisible();
  const emailInput = page.getByPlaceholder('seu@email.com');
  const senhaInput = page.getByPlaceholder('••••••••');
  await emailInput.fill(email);
  await senhaInput.fill(senha);
  await expect(emailInput).toHaveValue(email);
  await expect(senhaInput).toHaveValue(senha);
  await page.getByRole('button', { name: 'Entrar' }).click();

  // Gate redireciona para onboarding
  await page.waitForURL('**/onboarding');

  // Instrumentação: nenhuma escrita granular pode acontecer durante o wizard
  const escritasGranulares: string[] = [];
  let chamadasFinalizar = 0;
  page.on('request', (req) => {
    if (req.method() !== 'POST' && req.method() !== 'PUT') return;
    const url = req.url();
    if (url.includes('/onboarding/finalizar')) {
      chamadasFinalizar += 1;
    } else if (/\/(carteiras|contas|categorias|contas-fixas|metas)\b/.test(url)) {
      escritasGranulares.push(`${req.method()} ${url}`);
    }
  });

  // Passo 1 — Carteira
  await page.getByPlaceholder('0,00').first().fill('500');
  await page.getByPlaceholder('Ex: Nubank, Itaú').fill('Nubank');
  await page.getByRole('button', { name: /continuar/i }).click();

  // Passo 2 — Conta/Cartão
  await page.getByPlaceholder('0,00').first().fill('1000');
  await page.getByRole('button', { name: /continuar/i }).click();

  // Passo 3 — Categorias (todas pré-selecionadas)
  await page.getByRole('button', { name: /continuar/i }).click();

  // Passo 4 — Renda
  await page.getByPlaceholder('0,00').first().fill('4000');
  await page.getByRole('button', { name: /continuar/i }).click();

  // Passo 5 — Meta
  await page.getByPlaceholder('Ex: Reserva de emergência').fill('Reserva E2E');
  await page.locator('input[type="number"]').nth(0).fill('3000');
  await page.locator('input[type="number"]').nth(1).fill('300');
  await page.getByRole('button', { name: /continuar/i }).click();

  // Passo 6 — Confirmar: única escrita permitida é o /finalizar
  await expect(page.getByText('Reserva E2E')).toBeVisible();
  await page.getByRole('button', { name: /começar a usar/i }).click();

  // Dashboard com saldo da carteira criada
  await page.waitForURL('**/dashboard');
  await expect(page.getByText('Saldo Total')).toBeVisible();
  await expect(page.getByText('R$ 500,00').first()).toBeVisible();

  expect(chamadasFinalizar).toBe(1);
  expect(escritasGranulares).toEqual([]);
});
