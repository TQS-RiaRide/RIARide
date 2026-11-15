import { describe, expect, it, vi } from 'vitest';
import { fetchHealth } from './api';

describe('fetchHealth', () => {
  it('returns up when the backend reports UP', async () => {
    const fakeFetch = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP' }) });
    expect(await fetchHealth(fakeFetch as unknown as typeof fetch)).toBe('up');
    expect(fakeFetch).toHaveBeenCalledWith('/api/v1/health');
  });

  it('returns down when the backend reports a status other than UP', async () => {
    const fakeFetch = vi
      .fn()
      .mockResolvedValue({ ok: true, json: async () => ({ status: 'DOWN' }) });
    expect(await fetchHealth(fakeFetch as unknown as typeof fetch)).toBe('down');
  });

  it('returns down when the backend answers with an error', async () => {
    const fakeFetch = vi.fn().mockResolvedValue({ ok: false, json: async () => ({}) });
    expect(await fetchHealth(fakeFetch as unknown as typeof fetch)).toBe('down');
  });

  it('returns down when the backend is unreachable', async () => {
    const fakeFetch = vi.fn().mockRejectedValue(new Error('connection refused'));
    expect(await fetchHealth(fakeFetch as unknown as typeof fetch)).toBe('down');
  });
});
