import { expect, test } from '@playwright/test';
import { OPERATOR } from '../support/env';
import { logIn } from '../support/pages';

// RIA-86: logging out ends the Keycloak session and brings the user back to the app's landing
// page. Keycloak refuses a post-logout address the client does not list, so this also checks the
// realm's post.logout.redirect.uris against the address the app sends.
const STORY = { type: 'requirements', description: 'RIA-86' };

test.describe('Log out @RIA-86', { annotation: STORY }, () => {
  test('the user logs out and is back on the landing page, logged out', async ({ page }) => {
    await logIn(page, OPERATOR);

    await page.getByRole('button', { name: 'Log out' }).click();

    await expect(page.getByRole('button', { name: 'Log in', exact: true })).toBeVisible();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('navigation', { name: 'Workspaces' })).toHaveCount(0);
  });
});
