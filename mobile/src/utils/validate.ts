export const isValidCPF = (cpf: string): boolean => {
  const digits = cpf.replace(/\D/g, '');
  if (digits.length !== 11 || /^(\d)\1+$/.test(digits)) return false;
  let sum = 0;
  for (let i = 0; i < 9; i++) sum += parseInt(digits[i]) * (10 - i);
  let remainder = (sum * 10) % 11;
  if (remainder === 10 || remainder === 11) remainder = 0;
  if (remainder !== parseInt(digits[9])) return false;
  sum = 0;
  for (let i = 0; i < 10; i++) sum += parseInt(digits[i]) * (11 - i);
  remainder = (sum * 10) % 11;
  if (remainder === 10 || remainder === 11) remainder = 0;
  return remainder === parseInt(digits[10]);
};

export const isValidPhone = (phone: string): boolean =>
  /^\(\d{2}\) \d{4,5}-\d{4}$/.test(phone);

export const isValidCEP = (cep: string): boolean =>
  /^\d{5}-\d{3}$/.test(cep);

export const isValidEmail = (email: string): boolean =>
  /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim());

// Mesma regra do backend (@ValidPassword): minimo 8, ao menos 1 letra e 1 numero.
export const isValidPassword = (password: string): boolean =>
  password.length >= 8 && /[A-Za-z]/.test(password) && /\d/.test(password);

export const isValidDayOfMonth = (value: string | number): boolean => {
  const day = typeof value === 'number' ? value : Number(value);
  return Number.isInteger(day) && day >= 1 && day <= 31;
};
