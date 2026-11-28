// Where the stack runs and how the synthetic users log in. The password is the one
// infra/keycloak/create-dev-users.sh gives every dev user (DEV_USER_PASSWORD in infra/.env);
// it is read from the environment, never written in the tests.
export const APP_URL = process.env.E2E_BASE_URL ?? 'http://localhost:8081';
export const KEYCLOAK_URL = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8180';

export function devUserPassword(): string {
  const password = process.env.DEV_USER_PASSWORD;
  if (!password) {
    throw new Error('Set DEV_USER_PASSWORD to the password of the dev users (infra/.env)');
  }
  return password;
}

export const OPERATOR = 'operator.rodaleve';
export const TECHNICIAN = 'technician.rodaleve';
