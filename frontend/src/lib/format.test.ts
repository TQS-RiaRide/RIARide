import {
  formatDistance,
  VILA_RIA_TIME_ZONE,
  estimatedRangeKm,
  formatClock,
  formatDateTime,
  formatDay,
  formatDuration,
  formatMoney,
  formatPercent,
  formatRelative,
  formatTime,
  intlLocale,
} from './format';

describe('formatMoney', () => {
  it('shows cents as euros with two decimals in English', () => {
    expect(formatMoney(1250, 'EUR', 'en')).toBe('€12.50');
  });

  it('follows Portugal, with a decimal comma and the euro sign after the amount', () => {
    expect(formatMoney(1_250, 'EUR', 'pt')).toBe('12,50\u00a0€');
    expect(formatMoney(1_234_550, 'EUR', 'pt')).toContain('12\u00a0345,50');
  });

  it('shows zero cents as zero euros', () => {
    expect(formatMoney(0, 'EUR', 'en')).toBe('€0.00');
  });

  it('keeps a single cent', () => {
    expect(formatMoney(1, 'EUR', 'en')).toBe('€0.01');
  });

  it('shows a negative amount with a minus sign', () => {
    expect(formatMoney(-500, 'EUR', 'en')).toMatch(/^-€5\.00$/);
  });
});

describe('formatPercent', () => {
  it('rounds to whole percent by default', () => {
    expect(formatPercent(42.4, 'en')).toBe('42%');
  });

  it('keeps the requested fraction digits', () => {
    expect(formatPercent(42.5, 'en', 1)).toBe('42.5%');
  });

  it('pads to the requested fraction digits', () => {
    expect(formatPercent(42, 'en', 2)).toBe('42.00%');
  });

  it('uses a decimal comma in Portuguese', () => {
    expect(formatPercent(42.5, 'pt', 1)).toMatch(/^42,5\s?%$/);
  });

  it('shows zero and one hundred percent', () => {
    expect(formatPercent(0, 'en')).toBe('0%');
    expect(formatPercent(100, 'en')).toBe('100%');
  });
});

describe('formatDateTime', () => {
  it('uses the Lisbon time zone', () => {
    expect(VILA_RIA_TIME_ZONE).toBe('Europe/Lisbon');
  });

  it('shows summer time (UTC+1) in Lisbon in English', () => {
    const text = formatDateTime('2026-07-01T12:00:00Z', 'en');

    expect(text).toContain('Jul 1, 2026');
    expect(text).toContain('1:00');
    expect(text).toMatch(/PM/);
  });

  it('shows winter time (UTC+0) in Lisbon in Portuguese', () => {
    const text = formatDateTime('2026-01-01T12:00:00Z', 'pt');

    expect(text).toContain('2026');
    expect(text).toContain('12:00');
    expect(text).toContain('01/01/2026');
  });

  it('moves the date forward when Lisbon has passed midnight', () => {
    expect(formatDateTime('2026-06-30T23:30:00Z', 'en')).toContain('Jul 1, 2026');
  });
});

describe('formatTime', () => {
  it('shows only the Lisbon time in English', () => {
    expect(formatTime('2026-07-01T12:00:00Z', 'en')).toMatch(/^1:00\sPM$/);
  });

  it('shows a 24 hour Lisbon time in Portuguese', () => {
    expect(formatTime('2026-01-01T12:05:00Z', 'pt')).toBe('12:05');
  });
});

describe('formatDay', () => {
  it('shows a short weekday and day in English', () => {
    expect(formatDay('2026-11-28', 'en')).toBe('Sat, Nov 28');
  });

  it('shows a short weekday and day in Portuguese', () => {
    const text = formatDay('2026-11-28', 'pt');

    expect(text).toMatch(/^sáb/);
    expect(text).toContain('28/11');
  });

  it('does not shift the first or last day of a year', () => {
    expect(formatDay('2026-01-01', 'en')).toBe('Thu, Jan 1');
    expect(formatDay('2026-12-31', 'en')).toBe('Thu, Dec 31');
  });
});

describe('formatClock', () => {
  it('shows zero as 0:00:00', () => {
    expect(formatClock(0)).toBe('0:00:00');
  });

  it('pads minutes and seconds', () => {
    expect(formatClock(42 * 60 + 17)).toBe('0:42:17');
  });

  it('rolls over to hours at 3600 seconds', () => {
    expect(formatClock(3599)).toBe('0:59:59');
    expect(formatClock(3600)).toBe('1:00:00');
  });

  it('does not wrap after 24 hours', () => {
    expect(formatClock(25 * 3600)).toBe('25:00:00');
  });

  it('drops fractions of a second', () => {
    expect(formatClock(59.9)).toBe('0:00:59');
  });

  it('shows a negative time as zero', () => {
    expect(formatClock(-5)).toBe('0:00:00');
  });
});

describe('formatDuration', () => {
  it('shows minutes only under an hour', () => {
    expect(formatDuration(45, 'en')).toMatch(/^45\s?m(in)?$/);
  });

  it('shows hours only on a whole hour', () => {
    expect(formatDuration(180, 'en')).toMatch(/^3\s?h$/);
  });

  it('shows hours and minutes in Portuguese', () => {
    expect(formatDuration(192, 'pt')).toMatch(/^3\s?h 12\s?min$/);
  });

  it('shows zero minutes for zero', () => {
    expect(formatDuration(0, 'en')).toMatch(/^0\s?m(in)?$/);
  });

  it('rounds to the nearest minute', () => {
    expect(formatDuration(59.6, 'en')).toMatch(/^1\s?h$/);
    expect(formatDuration(0.4, 'en')).toMatch(/^0\s?m(in)?$/);
  });

  it('ignores the sign, which the caller shows', () => {
    expect(formatDuration(-75, 'pt')).toBe(formatDuration(75, 'pt'));
  });
});

describe('formatRelative', () => {
  const now = Date.parse('2026-11-25T12:00:00Z');
  const at = (offsetSeconds: number) => new Date(now + offsetSeconds * 1000).toISOString();

  it('says now for the current instant', () => {
    expect(formatRelative(at(0), now, 'en')).toBe('now');
  });

  it('says now, not "in 1 sec.", for a server time a few seconds ahead of the device', () => {
    expect(formatRelative(at(1), now, 'en')).toBe('now');
    expect(formatRelative(at(59), now, 'en')).toBe('now');
  });

  it('counts seconds under a minute', () => {
    const text = formatRelative(at(-59), now, 'en');

    expect(text).toContain('59');
    expect(text).toMatch(/sec/);
    expect(text).toMatch(/ago/);
  });

  it('switches to minutes at 60 seconds', () => {
    const text = formatRelative(at(60), now, 'en');

    expect(text).toMatch(/^in 1 min/);
  });

  it('counts minutes under an hour', () => {
    expect(formatRelative(at(-3599), now, 'en')).toMatch(/60 min/);
  });

  it('switches to hours at one hour', () => {
    expect(formatRelative(at(-3600), now, 'en')).toMatch(/1 hr/);
  });

  it('counts hours under a day', () => {
    expect(formatRelative(at(5 * 3600), now, 'en')).toMatch(/^in 5 hr/);
  });

  it('switches to days at one day', () => {
    expect(formatRelative(at(86_400), now, 'en')).toBe('tomorrow');
  });

  it('counts several days in the past in Portuguese', () => {
    const text = formatRelative(at(-3 * 86_400), now, 'pt');

    expect(text).toContain('3');
    expect(text).toMatch(/^há/);
  });

  it('says tomorrow in Portuguese', () => {
    expect(formatRelative(at(86_400), now, 'pt')).toBe('amanhã');
  });
});

describe('estimatedRangeKm', () => {
  it('gives the full 55 km on a full battery', () => {
    expect(estimatedRangeKm(100)).toBe(55);
  });

  it('gives no range on an empty battery', () => {
    expect(estimatedRangeKm(0)).toBe(0);
  });

  it('rounds to the nearest kilometre', () => {
    expect(estimatedRangeKm(20)).toBe(11);
    expect(estimatedRangeKm(50)).toBe(28);
    expect(estimatedRangeKm(1)).toBe(1);
  });
});

describe('intlLocale', () => {
  it('reads Portuguese as Portugal, not Brazil', () => {
    expect(intlLocale('pt')).toBe('pt-PT');
    expect(intlLocale('pt-BR')).toBe('pt-PT');
  });

  it('keeps other languages as they are', () => {
    expect(intlLocale('en')).toBe('en');
    expect(intlLocale('en-GB')).toBe('en-GB');
  });
});

describe('formatDistance', () => {
  it('gives metres below one kilometre', () => {
    expect(formatDistance(89, 'en')).toBe('89 m');
    expect(formatDistance(999, 'en')).toBe('999 m');
  });

  it("gives kilometres with one decimal from one kilometre, in the reader's notation", () => {
    expect(formatDistance(1000, 'en')).toBe('1 km');
    expect(formatDistance(1625, 'en')).toBe('1.6 km');
    expect(formatDistance(1625, 'pt')).toBe('1,6 km');
  });
});
