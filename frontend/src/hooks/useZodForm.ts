import { useCallback, useState } from 'react';
import type { z } from 'zod';

export type FormErrors = Record<string, string>;

export function issuesToFieldErrors(issues: readonly z.core.$ZodIssue[]): FormErrors {
  const errors: FormErrors = {};
  for (const issue of issues) {
    const field = issue.path.join('.') || 'form';
    errors[field] ??= issue.message;
  }
  return errors;
}

function focusFirstInvalidField(errors: FormErrors) {
  const firstField = Object.keys(errors).find(field => field !== 'form');
  if (!firstField) return;
  requestAnimationFrame(() => {
    const element = document.getElementsByName(firstField).item(0) as HTMLElement | null;
    element?.focus();
  });
}

export function useZodForm<TSchema extends z.ZodType>(schema: TSchema) {
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitted, setSubmitted] = useState(false);

  const validate = useCallback((input: unknown): z.output<TSchema> | null => {
    setSubmitted(true);
    const result = schema.safeParse(input);
    if (result.success) {
      setErrors({});
      return result.data as z.output<TSchema>;
    }
    const nextErrors = issuesToFieldErrors(result.error.issues);
    setErrors(nextErrors);
    focusFirstInvalidField(nextErrors);
    return null;
  }, [schema]);

  const revalidateField = useCallback((field: string, input: unknown) => {
    if (!submitted) return;
    const result = schema.safeParse(input);
    const message = result.success
      ? undefined
      : issuesToFieldErrors(result.error.issues)[field];
    setErrors(current => {
      const next = { ...current };
      if (message) next[field] = message;
      else delete next[field];
      return next;
    });
  }, [schema, submitted]);

  const resetValidation = useCallback(() => {
    setErrors({});
    setSubmitted(false);
  }, []);

  return { errors, validate, revalidateField, resetValidation };
}
