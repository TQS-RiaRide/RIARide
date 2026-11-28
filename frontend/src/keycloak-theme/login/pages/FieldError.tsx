import { kcSanitize } from 'keycloakify/lib/kcSanitize';

// Keycloak's message for a refused field, as the app shows a refused form: one block under it.
export default function FieldError({ id, text }: { id: string; text: string }) {
  if (!text) return null;
  return (
    <p
      id={id}
      role="alert"
      className="form-error"
      dangerouslySetInnerHTML={{ __html: kcSanitize(text) }}
    />
  );
}
