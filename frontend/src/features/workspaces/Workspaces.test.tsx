import { beforeEach, describe, expect, it } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import i18n from '../../i18n';
import Workspaces from './Workspaces';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

function menu() {
  return screen.getByRole('navigation', { name: 'Workspaces' });
}

describe('Workspaces', () => {
  // Acceptance criterion of RIA-86.
  it('shows an operator the operator dashboard and not the hotel backoffice', () => {
    render(<Workspaces workspaces={['OPERATOR_DASHBOARD', 'MAINTENANCE_TICKETS']} />);

    expect(menu()).toHaveTextContent('Operator dashboard');
    expect(menu()).toHaveTextContent('Maintenance tickets');
    expect(menu()).not.toHaveTextContent('Hotel backoffice');
  });

  it('opens the first workspace and marks it as the current one', () => {
    render(<Workspaces workspaces={['OPERATOR_DASHBOARD', 'MAINTENANCE_TICKETS']} />);

    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Operator dashboard');
    expect(screen.getByRole('button', { name: 'Operator dashboard' })).toHaveAttribute(
      'aria-current',
      'true',
    );
    expect(screen.getByRole('button', { name: 'Maintenance tickets' })).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('opens another workspace from the menu', () => {
    render(<Workspaces workspaces={['OPERATOR_DASHBOARD', 'MAINTENANCE_TICKETS']} />);

    fireEvent.click(screen.getByRole('button', { name: 'Maintenance tickets' }));

    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Maintenance tickets');
    expect(screen.getByRole('button', { name: 'Maintenance tickets' })).toHaveAttribute(
      'aria-current',
      'true',
    );
  });

  it.each([
    ['GUEST_DASHBOARD', 'My trips'],
    ['MAINTENANCE_TICKETS', 'Maintenance tickets'],
    ['HOTEL_BACKOFFICE', 'Hotel backoffice'],
    ['ACCOUNT_ADMINISTRATION', 'Accounts and roles'],
  ] as const)('names the %s workspace "%s"', (workspace, title) => {
    render(<Workspaces workspaces={[workspace]} />);

    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(title);
  });

  it('tells a user without a role that they have nothing to open', () => {
    render(<Workspaces workspaces={[]} />);

    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Your account has no role yet.');
  });

  it('shows the menu in Portuguese', async () => {
    render(<Workspaces workspaces={['HOTEL_BACKOFFICE']} />);

    await act(async () => {
      await i18n.changeLanguage('pt');
    });

    expect(screen.getByRole('navigation', { name: 'Áreas' })).toHaveTextContent(
      'Backoffice do hotel',
    );
  });
});
