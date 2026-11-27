import { expect, test } from '@playwright/test';
import { OPERATOR, TECHNICIAN } from '../support/env';
import { expectNoSeriousAccessibilityViolations, logIn, testData } from '../support/pages';

// RIA-92: an operator opens a maintenance ticket for a faulty bike from the web UI (core use case,
// QA Manual 5.3). The rules behind it are covered by Cucumber; this covers the UI path.
// The "requirements" annotation becomes a JUnit property, so Xray links each result to the story.
const STORY = { type: 'requirements', description: 'RIA-92' };

test.describe('Open a maintenance ticket @RIA-92', { annotation: STORY }, () => {
  test('the operator opens a ticket and the bike goes to maintenance with a 24-hour deadline', async ({
    page,
  }) => {
    const { available } = testData();
    await logIn(page, OPERATOR);

    await page.getByRole('link', { name: 'Maintenance tickets' }).click();
    await page.getByLabel('Bike code').fill(available);
    await page.getByLabel('Fault').fill('Rear brake does not work');
    await page.getByRole('button', { name: 'Open ticket' }).click();

    const status = page.getByRole('status').filter({ hasText: 'Ticket opened' });
    await expect(status).toContainText(`Ticket opened for ${available}.`);
    await expect(status).toContainText('The bike is in maintenance until it is repaired.');
    await expect(status).toContainText('Repair deadline:');
    await expect(page.getByLabel('Bike code')).toHaveValue('');
  });

  test('the operator is told that a retired bike cannot get a ticket', async ({ page }) => {
    const { retired } = testData();
    await logIn(page, OPERATOR);

    await page.getByRole('link', { name: 'Maintenance tickets' }).click();
    await page.getByLabel('Bike code').fill(retired);
    await page.getByLabel('Fault').fill('Flat tyre');
    await page.getByRole('button', { name: 'Open ticket' }).click();

    await expect(page.getByRole('alert')).toHaveText(
      'This bike is retired, so it cannot get a ticket.',
    );
    await expect(page.getByLabel('Bike code')).toHaveValue(retired);
  });

  test('the empty fault is marked as the invalid field', async ({ page }) => {
    const { available } = testData();
    await logIn(page, OPERATOR);

    await page.getByRole('link', { name: 'Maintenance tickets' }).click();
    await page.getByLabel('Bike code').fill(available);
    await page.getByRole('button', { name: 'Open ticket' }).click();

    await expect(page.getByRole('alert')).toHaveText('Describe the fault in up to 500 characters.');
    await expect(page.getByLabel('Fault')).toHaveAttribute('aria-invalid', 'true');
  });

  test('a technician sees the tickets workspace without the form', async ({ page }) => {
    await logIn(page, TECHNICIAN);

    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Maintenance tickets');
    await expect(page.getByRole('heading', { name: 'Open a ticket' })).toHaveCount(0);
  });

  test('the ticket form has no serious accessibility violation, in English and Portuguese', async ({
    page,
  }) => {
    await logIn(page, OPERATOR);
    await page.getByRole('link', { name: 'Maintenance tickets' }).click();
    await expect(page.getByRole('heading', { name: 'Open a ticket' })).toBeVisible();

    await expectNoSeriousAccessibilityViolations(page);

    await page.getByLabel('Language').selectOption('pt');
    await expect(page.getByRole('heading', { name: 'Abrir um pedido' })).toBeVisible();
    await expectNoSeriousAccessibilityViolations(page);
  });
});
