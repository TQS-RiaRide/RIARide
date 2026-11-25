import { mkdir, writeFile } from 'node:fs/promises';
import { hubWithBikes, retire, tokenFor } from './api';
import { OPERATOR } from './env';

export const DATA_FILE = '.auth/data.json';

export interface TestData {
  available: string;
  retired: string;
}

// Creates this run's bikes once: an available one to open a ticket for, and a retired one,
// which can never get a ticket.
export default async function globalSetup(): Promise<void> {
  const run = Date.now().toString(36).toUpperCase();
  const token = await tokenFor(OPERATOR);
  const [available, retired] = await hubWithBikes(token, run, [`E2E-${run}-A`, `E2E-${run}-R`]);
  await retire(token, retired);
  const data: TestData = { available: available.code, retired: retired.code };
  await mkdir('.auth', { recursive: true });
  await writeFile(DATA_FILE, JSON.stringify(data));
}
