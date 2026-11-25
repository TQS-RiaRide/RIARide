import { dataMode } from './config';

describe('dataMode', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('is demo only for the value demo', () => {
    expect(dataMode('demo')).toBe('demo');
  });

  it.each(['live', 'DEMO', '', 'anything'])('is live for %j', (value) => {
    expect(dataMode(value)).toBe('live');
  });

  it('is live when nothing is set', () => {
    vi.stubEnv('VITE_DATA_MODE', undefined);

    expect(dataMode()).toBe('live');
  });

  it('reads the build time setting by default', () => {
    vi.stubEnv('VITE_DATA_MODE', 'demo');

    expect(dataMode()).toBe('demo');
  });

  it('is demo when Vite runs in demo mode and nothing else is set', () => {
    vi.stubEnv('VITE_DATA_MODE', undefined);
    vi.stubEnv('MODE', 'demo');

    expect(dataMode()).toBe('demo');
  });
});
