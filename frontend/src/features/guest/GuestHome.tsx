import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { toast } from 'sonner';
import { PATHS } from '../../app/paths';
import { BASE_PASS_PRICES, isActiveTrip } from '../../backend/liveBackend';
import {
  useCurrentPass,
  useHubs,
  useMyTrips,
  usePassPrices,
  useStartTrip,
} from '../../backend/queries';
import { Icon } from '../../components/ui/ui';
import { errorMessageKey, useErrorText } from '../../lib/errors';
import { formatMoney } from '../../lib/format';
import { useAuth } from '../auth/useAuth';
import BikeCodeSheet from './BikeCodeSheet';
import BuyPassSheet from './BuyPassSheet';
import HubVehicles from './HubVehicles';
import PassCard from './PassCard';
import { cheapestPrice } from './prices';
import './guest.css';

function greetingKey(hour: number): 'morning' | 'afternoon' | 'evening' {
  if (hour < 12) return 'morning';
  if (hour < 19) return 'afternoon';
  return 'evening';
}

// The guest's home: their pass, a running trip if there is one, and the bikes at the chosen hub.
// The chosen hub lives in the URL (?hub=), so it survives a reload and can be shared.
export default function GuestHome() {
  const { t, i18n } = useTranslation('guest');
  const { state } = useAuth();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const pass = useCurrentPass();
  const prices = usePassPrices();
  const hubs = useHubs();
  const trips = useMyTrips();
  const start = useStartTrip();
  const errorText = useErrorText();
  const [buyOpen, setBuyOpen] = useState(false);
  const [codeOpen, setCodeOpen] = useState(false);

  const firstName = state.status === 'authenticated' ? (state.user.name ?? '').split(' ')[0] : '';
  const activeTrip = trips.data?.find(isActiveTrip);
  const hubId = params.get('hub') ?? hubs.data?.[0]?.id;
  // Without a pass an unlock can only fail, so buying one is the next step (BR1, BR4).
  const needsPass = pass.isSuccess && (!pass.data || pass.data.status === 'CLOSED');
  const cheapest = cheapestPrice(prices.data ?? BASE_PASS_PRICES);
  const fromPrice = cheapest
    ? formatMoney(cheapest.priceCents, cheapest.currency, i18n.language)
    : undefined;

  const startTrip = (vehicleCode: string) => {
    if (needsPass) {
      setCodeOpen(false);
      setBuyOpen(true);
      return;
    }
    start.mutate(vehicleCode, {
      onSuccess: (trip) => {
        setCodeOpen(false);
        void navigate(PATHS.guestTrip(trip.id), { viewTransition: true });
      },
      onError: (error) => {
        const map = {
          '403:No valid pass': 'guest:start.errors.noPass',
          '403': 'guest:start.errors.noStay',
          '404': 'guest:start.errors.notFound',
          '409:Trip already in progress': 'guest:start.errors.oneAtATime',
          '409:Reservation held for another bike': 'guest:start.errors.reservedElsewhere',
          '409': 'guest:start.errors.unavailable',
        };
        const needsPass = errorMessageKey(error, map) === 'guest:start.errors.noPass';
        toast.error(
          errorText(error, map),
          needsPass
            ? { action: { label: t('pass.buy'), onClick: () => setBuyOpen(true) } }
            : undefined,
        );
      },
    });
  };

  return (
    <div className="guest-home">
      {/* On wide screens this column stays beside the bikes: who you are, your trip, your pass. */}
      <div className="guest-side">
        <section className="guest-hello">
          <h1 className="guest-title display">
            {firstName
              ? t('home.hello', { context: greetingKey(new Date().getHours()), name: firstName })
              : t('home.helloAnon')}
          </h1>
        </section>

        {activeTrip && (
          <Link to={PATHS.guestTrip(activeTrip.id)} viewTransition className="trip-banner sq">
            <span className="trip-banner-dot" aria-hidden="true" />
            <span className="trip-banner-text">
              {t('home.activeTrip')} <span className="num">{activeTrip.vehicleCode}</span>
            </span>
            <Icon name="chevron" size={18} />
          </Link>
        )}

        <PassCard
          pass={pass.data}
          loading={pass.isPending}
          onBuy={() => setBuyOpen(true)}
          fromPrice={fromPrice}
        />
      </div>

      <section className="hub-section" aria-labelledby="hub-heading">
        <div className="hub-section-head">
          <h2 id="hub-heading" className="section-title">
            {t('home.bikes')}
          </h2>
          <button type="button" className="text-btn" onClick={() => setCodeOpen(true)}>
            {t('home.haveCode')}
          </button>
        </div>

        {(hubs.data?.length ?? 0) > 1 && (
          <div className="hub-chips" role="tablist" aria-label={t('home.hubs')}>
            {hubs.data?.map((h) => (
              <button
                key={h.id}
                type="button"
                role="tab"
                aria-selected={h.id === hubId}
                className="hub-chip"
                onClick={() =>
                  setParams({ hub: h.id }, { replace: true, preventScrollReset: true })
                }
              >
                {h.name}
                <span className="num hub-chip-count">{h.dockedBikes}</span>
              </button>
            ))}
          </div>
        )}

        <HubVehicles
          hubId={hubId}
          hubsLoading={hubs.isPending}
          startingCode={start.isPending ? start.variables : undefined}
          onStart={startTrip}
          quiet={needsPass}
          onUseCode={() => setCodeOpen(true)}
        />
      </section>

      <BuyPassSheet open={buyOpen} onOpenChange={setBuyOpen} />
      <BikeCodeSheet
        open={codeOpen}
        onOpenChange={setCodeOpen}
        onStart={startTrip}
        starting={start.isPending}
      />
    </div>
  );
}
