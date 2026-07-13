export function fieldA11y(name: string, error?: string) {
  return {
    name,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': error ? `${name.replaceAll('.', '-')}-error` : undefined,
  };
}
