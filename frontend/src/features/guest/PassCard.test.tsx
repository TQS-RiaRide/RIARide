import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Pass } from '../../backend/types';
import i18n from '../../i18n';
import PassCard from './PassCard';

const pass: Pass = {
  id: 'pass-1',
  passType: 'THREE_DAY',
  status: 'INACTIVE',
  priceCents: 2500,
  depositCents: 5000,
  currency: 'EUR',
};

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

describe('PassCard', () => {
  it('marks the card busy while the pass loads', () => {
    render(<PassCard pass={undefined} loading onBuy={vi.fn()} fromPrice="€10.00" />);

    expect(screen.getByRole('region', { name: 'Your pass' })).toHaveAttribute('aria-busy', 'true');
  });

  it('offers to buy a pass when the guest has none', () => {
    const onBuy = vi.fn();
    render(<PassCard pass={null} loading={false} onBuy={onBuy} fromPrice="€10.00" />);

    fireEvent.click(screen.getByRole('button', { name: 'Buy a pass from €10.00' }));

    expect(screen.getByRole('heading', { name: 'No pass yet' })).toBeInTheDocument();
    expect(screen.getByText('It starts on your first ride.')).toBeInTheDocument();
    expect(onBuy).toHaveBeenCalledOnce();
  });

  it('offers a pass without a price while the prices are unknown', () => {
    render(<PassCard pass={null} loading={false} onBuy={vi.fn()} fromPrice={undefined} />);

    expect(screen.getByRole('button', { name: 'Buy a pass' })).toBeInTheDocument();
  });

  it('offers a new pass when the last one has ended', () => {
    render(
      <PassCard
        pass={{ ...pass, status: 'CLOSED' }}
        loading={false}
        onBuy={vi.fn()}
        fromPrice="€10.00"
      />,
    );

    expect(screen.getByRole('heading', { name: 'Pass ended' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Buy a pass from €10.00' })).toBeInTheDocument();
  });

  // BR4: a pass bought but not used yet starts on the first trip.
  it('shows a bought pass as not started, with its rule and the deposit held', () => {
    render(<PassCard pass={pass} loading={false} onBuy={vi.fn()} fromPrice="€10.00" />);

    expect(screen.getByText('3-day pass')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Not started' })).toBeInTheDocument();
    expect(
      screen.getByText('It starts on your first trip. 72 hours from your first trip.'),
    ).toBeInTheDocument();
    expect(screen.getByText('€50.00')).toBeInTheDocument();
  });

  it('shows an active pass', () => {
    render(
      <PassCard
        pass={{ ...pass, status: 'ACTIVE' }}
        loading={false}
        onBuy={vi.fn()}
        fromPrice="€10.00"
      />,
    );

    expect(screen.getByRole('heading', { name: 'Active' })).toBeInTheDocument();
    expect(screen.getByText('72 hours from your first trip.')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('shows a pass whose payment is still being confirmed', () => {
    render(
      <PassCard
        pass={{ ...pass, status: 'PENDING' }}
        loading={false}
        onBuy={vi.fn()}
        fromPrice="€10.00"
      />,
    );

    expect(screen.getByRole('heading', { name: 'Almost ready' })).toBeInTheDocument();
    expect(screen.getByText('Confirming payment…')).toBeInTheDocument();
  });
});
