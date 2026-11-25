import { fireEvent, render, screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import i18n from '../../i18n';
import BikeCodeSheet from './BikeCodeSheet';

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

function renderSheet(starting = false) {
  const onStart = vi.fn();
  render(<BikeCodeSheet open onOpenChange={vi.fn()} onStart={onStart} starting={starting} />);
  const sheet = screen.getByRole('dialog', { name: 'Start with a bike code' });
  return { onStart, sheet };
}

describe('BikeCodeSheet', () => {
  it('keeps the unlock button disabled until a code is typed', () => {
    const { sheet } = renderSheet();

    fireEvent.change(within(sheet).getByLabelText('Bike code'), { target: { value: '   ' } });

    expect(within(sheet).getByRole('button', { name: 'Unlock' })).toBeDisabled();
    expect(within(sheet).getByLabelText('Bike code')).toHaveAttribute('placeholder', 'e.g. EB-401');
  });

  it('starts the trip with the code trimmed and in capitals', () => {
    const { onStart, sheet } = renderSheet();

    fireEvent.change(within(sheet).getByLabelText('Bike code'), {
      target: { value: '  eb-388 ' },
    });
    fireEvent.click(within(sheet).getByRole('button', { name: 'Unlock' }));

    expect(onStart).toHaveBeenCalledWith('EB-388');
  });

  it('ignores a submit with no code', () => {
    const { onStart, sheet } = renderSheet();

    fireEvent.submit(within(sheet).getByRole('button', { name: 'Unlock' }));

    expect(onStart).not.toHaveBeenCalled();
  });

  it('marks the button busy and disabled while the bike unlocks', () => {
    const { sheet } = renderSheet(true);

    fireEvent.change(within(sheet).getByLabelText('Bike code'), { target: { value: 'EB-1' } });

    const button = within(sheet).getByRole('button', { name: 'Unlock' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
  });
});
