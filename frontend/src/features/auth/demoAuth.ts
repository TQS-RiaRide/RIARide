import type { Workspace } from '../workspaces/workspace';
import type { CurrentUser } from './api';
import type { AuthClient } from './authClient';

// Demo mode only: synthetic personas (Product Specification, section 2.2) stand in for Keycloak.
// The "token" is the persona id; nothing here reaches a server.

export type PersonaId = 'guest' | 'operator' | 'technician' | 'manager' | 'admin';

export interface Persona {
  id: PersonaId;
  name: string;
  email: string;
  roles: string[];
  workspaces: Workspace[];
}

export const PERSONAS: readonly Persona[] = [
  {
    id: 'guest',
    name: 'Sophie Laurent',
    email: 'sophie@example.test',
    roles: ['GUEST'],
    workspaces: ['GUEST_DASHBOARD'],
  },
  {
    id: 'operator',
    name: 'Rui Matos',
    email: 'rui@rodaleve.test',
    roles: ['OPERATOR'],
    workspaces: ['OPERATOR_DASHBOARD', 'MAINTENANCE_TICKETS'],
  },
  {
    id: 'technician',
    name: 'Sara Lopes',
    email: 'sara@rodaleve.test',
    roles: ['TECHNICIAN'],
    workspaces: ['MAINTENANCE_TICKETS'],
  },
  {
    id: 'manager',
    name: 'Ana Ribeiro',
    email: 'ana@mares.test',
    roles: ['HOTEL_MANAGER'],
    workspaces: ['HOTEL_BACKOFFICE'],
  },
  {
    id: 'admin',
    name: 'Ana Teixeira',
    email: 'admin@riaride.test',
    roles: ['ADMIN'],
    workspaces: [
      'OPERATOR_DASHBOARD',
      'MAINTENANCE_TICKETS',
      'HOTEL_BACKOFFICE',
      'ACCOUNT_ADMINISTRATION',
    ],
  },
];

export const PERSONA_KEY = 'riaride.demo.persona';

type SessionStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;

function sessionStore(): SessionStorage | undefined {
  try {
    return window.sessionStorage;
  } catch {
    return undefined;
  }
}

export function findPersona(id: string | null | undefined): Persona | undefined {
  return PERSONAS.find((persona) => persona.id === id);
}

export interface DemoAuthClient extends AuthClient {
  personas: readonly Persona[];
  signInAs(personaId: string): void;
}

export function createDemoAuthClient(
  storage: SessionStorage | undefined = sessionStore(),
  navigate: (url: string) => void = (url) => window.location.assign(url),
): DemoAuthClient {
  const current = () => findPersona(storage?.getItem(PERSONA_KEY));
  return {
    personas: PERSONAS,
    async init() {
      return current() !== undefined;
    },
    async login() {
      navigate('/sign-in');
    },
    async logout() {
      storage?.removeItem(PERSONA_KEY);
      navigate('/');
    },
    async getToken() {
      const persona = current();
      if (!persona) throw new Error('Not logged in');
      return persona.id;
    },
    signInAs(personaId) {
      if (!findPersona(personaId)) return;
      storage?.setItem(PERSONA_KEY, personaId);
      navigate('/');
    },
  };
}

export async function loadDemoUser(token: string): Promise<CurrentUser> {
  const persona = findPersona(token);
  if (!persona) throw new Error('Unknown demo persona');
  return {
    id: persona.id,
    name: persona.name,
    email: persona.email,
    roles: [...persona.roles],
    workspaces: [...persona.workspaces],
  };
}
