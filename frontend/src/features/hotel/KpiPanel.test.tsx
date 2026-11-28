import { screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Backend } from '../../backend/Backend';
import { createDemoBackend } from '../../backend/demoBackend';
import { DEMO_KPIS } from '../../backend/demoData';
import i18n from '../../i18n';
import { renderApp } from '../../testing/renderApp';

// Business KPIs against the SLA and technical metrics (RIA-122, RIA-123).

beforeEach(async () => {
  await i18n.changeLanguage('en');
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.localStorage.clear();
});

function withKpis(kpis: Awaited<ReturnType<Backend['kpis']>>): Backend {
  return { ...createDemoBackend({ latencyMs: 0 }), kpis: async () => kpis };
}

async function kpiPanel(backend?: Backend) {
  await renderApp({ path: '/hotel', user: 'manager', backend });
  return screen.findByRole('region', { name: 'Service indicators' });
}

describe('KpiPanel', () => {
  it('shows availability against the SLA and marks the hotels below it', async () => {
    const panel = await kpiPanel();

    expect(await within(panel).findByText('Availability against the SLA')).toBeInTheDocument();
    expect(within(panel).getByText('97%', { selector: '.sla-value' })).toBeInTheDocument();
    const salinas = within(panel).getByText('Marés Salinas').closest('li');
    expect(salinas).toHaveTextContent('Below the SLA: 89%');
    const centro = within(panel).getByText('Marés Centro').closest('li');
    expect(centro).not.toHaveTextContent('Below the SLA');
    expect(within(panel).queryByText('Sample data')).not.toBeInTheDocument();
  });

  it('shows trips per day, business figures and technical metrics', async () => {
    const panel = await kpiPanel();

    expect(await within(panel).findByText('average 58')).toBeInTheDocument();
    expect(
      within(panel).getByRole('img', {
        name: 'Trips per day over the last 14 days, between 38 and 92.',
      }),
    ).toBeInTheDocument();
    expect(within(panel).getByText('38%')).toBeInTheDocument();
    expect(within(panel).getByText('14 h')).toBeInTheDocument();
    expect(within(panel).getByText('212')).toBeInTheDocument();
    expect(within(panel).getByText('182 ms')).toBeInTheDocument();
    expect(within(panel).getByText('0.3%')).toBeInTheDocument();
    expect(within(panel).getByText('1.4 s')).toBeInTheDocument();
  });

  it('raises no SLA alert when every hotel meets the target', async () => {
    const panel = await kpiPanel(
      withKpis({
        ...DEMO_KPIS,
        availability: [
          { hotel: 'Marés Centro', availabilityPct: 95 },
          { hotel: 'Marés Praia', availabilityPct: 99 },
        ],
      }),
    );

    expect(await within(panel).findByText('95%', { selector: '.sla-value' })).toBeInTheDocument();
    expect(within(panel).queryByText(/Below the SLA/)).not.toBeInTheDocument();
  });

  it('averages no trips as zero', async () => {
    const panel = await kpiPanel(withKpis({ ...DEMO_KPIS, tripsPerDay: [] }));

    expect(await within(panel).findByText('average 0')).toBeInTheDocument();
    expect(within(panel).queryByRole('img')).not.toBeInTheDocument();
  });

  it('says the indicators are not available when the platform cannot compute them', async () => {
    const panel = await kpiPanel(withKpis(null));

    expect(
      await within(panel).findByText('The indicators are not available yet'),
    ).toBeInTheDocument();
    expect(
      within(panel).getByText('They appear here once the platform computes them.'),
    ).toBeInTheDocument();
  });
});
