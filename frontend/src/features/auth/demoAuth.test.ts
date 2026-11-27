import { PERSONAS, PERSONA_KEY, createDemoAuthClient, findPersona, loadDemoUser } from './demoAuth';

function memoryStorage(initial: Record<string, string> = {}) {
  const values = new Map(Object.entries(initial));
  return {
    getItem: vi.fn((key: string) => values.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => {
      values.set(key, value);
    }),
    removeItem: vi.fn((key: string) => {
      values.delete(key);
    }),
  };
}

describe('findPersona', () => {
  it.each(['guest', 'operator', 'technician', 'manager', 'admin'])('finds the %s persona', (id) => {
    expect(findPersona(id)?.id).toBe(id);
  });

  it.each([null, undefined, '', 'GUEST', 'root'])('finds no persona for %s', (id) => {
    expect(findPersona(id)).toBeUndefined();
  });
});

describe('createDemoAuthClient', () => {
  it('is logged out when no persona is stored', async () => {
    const client = createDemoAuthClient(memoryStorage(), vi.fn());

    await expect(client.init()).resolves.toBe(false);
  });

  it('is logged in when a known persona is stored', async () => {
    const client = createDemoAuthClient(memoryStorage({ [PERSONA_KEY]: 'operator' }), vi.fn());

    await expect(client.init()).resolves.toBe(true);
  });

  it('is logged out when the stored persona is unknown', async () => {
    const client = createDemoAuthClient(memoryStorage({ [PERSONA_KEY]: 'intruder' }), vi.fn());

    await expect(client.init()).resolves.toBe(false);
  });

  it('sends the browser to the persona picker on login', async () => {
    const navigate = vi.fn();
    const client = createDemoAuthClient(memoryStorage(), navigate);

    await client.login();

    expect(navigate).toHaveBeenCalledWith('/sign-in');
  });

  it('forgets the persona and goes home on logout', async () => {
    const storage = memoryStorage({ [PERSONA_KEY]: 'guest' });
    const navigate = vi.fn();
    const client = createDemoAuthClient(storage, navigate);

    await client.logout();

    expect(storage.removeItem).toHaveBeenCalledWith(PERSONA_KEY);
    expect(navigate).toHaveBeenCalledWith('/');
    await expect(client.init()).resolves.toBe(false);
  });

  it('gives the persona id as the token when logged in', async () => {
    const client = createDemoAuthClient(memoryStorage({ [PERSONA_KEY]: 'manager' }), vi.fn());

    await expect(client.getToken()).resolves.toBe('manager');
  });

  it('refuses a token when logged out', async () => {
    const client = createDemoAuthClient(memoryStorage(), vi.fn());

    await expect(client.getToken()).rejects.toThrow('Not logged in');
  });

  it('stores a known persona and goes home on sign in', async () => {
    const storage = memoryStorage();
    const navigate = vi.fn();
    const client = createDemoAuthClient(storage, navigate);

    client.signInAs('technician');

    expect(storage.setItem).toHaveBeenCalledWith(PERSONA_KEY, 'technician');
    expect(navigate).toHaveBeenCalledWith('/');
    await expect(client.getToken()).resolves.toBe('technician');
  });

  it('ignores an unknown persona on sign in', async () => {
    const storage = memoryStorage();
    const navigate = vi.fn();
    const client = createDemoAuthClient(storage, navigate);

    client.signInAs('superuser');

    expect(storage.setItem).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    await expect(client.init()).resolves.toBe(false);
  });

  it('works with no storage at all, as when session storage is blocked', async () => {
    const navigate = vi.fn();
    const client = createDemoAuthClient(undefined, navigate);

    client.signInAs('guest');
    await client.logout();

    await expect(client.init()).resolves.toBe(false);
    expect(navigate).toHaveBeenCalledTimes(2);
  });

  describe('with the defaults', () => {
    afterEach(() => {
      vi.restoreAllMocks();
      window.sessionStorage.clear();
    });

    it('uses the session storage of the browser', async () => {
      window.sessionStorage.setItem(PERSONA_KEY, 'admin');

      const client = createDemoAuthClient();

      await expect(client.getToken()).resolves.toBe('admin');
    });

    it('treats a session storage that throws as no storage', async () => {
      vi.spyOn(window, 'sessionStorage', 'get').mockImplementation(() => {
        throw new Error('SecurityError');
      });

      const client = createDemoAuthClient();

      await expect(client.init()).resolves.toBe(false);
    });
  });
});

describe('loadDemoUser', () => {
  it('gives the user of a known persona', async () => {
    const user = await loadDemoUser('operator');

    expect(user).toEqual({
      id: 'operator',
      name: 'Rui Matos',
      email: 'rui@rodaleve.test',
      roles: ['OPERATOR'],
      workspaces: ['OPERATOR_DASHBOARD', 'MAINTENANCE_TICKETS'],
    });
  });

  it('copies the roles and workspaces so the personas cannot be changed', async () => {
    const user = await loadDemoUser('admin');
    const admin = PERSONAS.find((persona) => persona.id === 'admin');

    expect(user.roles).not.toBe(admin?.roles);
    expect(user.workspaces).not.toBe(admin?.workspaces);
  });

  it('refuses an unknown persona', async () => {
    await expect(loadDemoUser('nobody')).rejects.toThrow('Unknown demo persona');
  });
});
