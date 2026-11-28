import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { devUserPassword } from './env';
import type { TestData } from './global-setup';

export function testData(): TestData {
  return JSON.parse(readFileSync('.auth/data.json', 'utf8')) as TestData;
}

// Logs in through the real Keycloak page, as a person would.
export async function logIn(page: Page, username: string): Promise<void> {
  await page.goto('/');
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  await page.getByLabel('Email or username').fill(username);
  await page.getByLabel('Password', { exact: true }).fill(devUserPassword());
  await page.getByRole('button', { name: 'Log in', exact: true }).click();
  await expect(page.getByRole('navigation', { name: 'Workspaces' })).toBeVisible();
}

// QA Manual 5.4 (T8): no serious or critical WCAG 2.1 AA violation on a core page.
export async function expectNoSeriousAccessibilityViolations(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const serious = results.violations.filter(
    (violation) => violation.impact === 'serious' || violation.impact === 'critical',
  );
  expect(serious.map((violation) => `${violation.id}: ${violation.help}`)).toEqual([]);
}
