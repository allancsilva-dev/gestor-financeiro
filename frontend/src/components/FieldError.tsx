interface FieldErrorProps {
  name: string;
  error?: string;
}

function fieldErrorId(name: string) {
  return `${name.replaceAll('.', '-')}-error`;
}

export default function FieldError({ name, error }: FieldErrorProps) {
  if (!error) return null;
  return <p id={fieldErrorId(name)} role="alert" className="mt-1 text-sm text-red-400">{error}</p>;
}
