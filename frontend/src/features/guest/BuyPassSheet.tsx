import { useEffect, useId, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { newIdempotencyKey } from '../../backend/http';
import { isProblem, NetworkError } from '../../backend/problem';
import { useBuyPass, usePassPrices } from '../../backend/queries';
import type { PassType } from '../../backend/types';
import Sheet from '../../components/ui/Sheet';
import { Button, Skeleton } from '../../components/ui/ui';
import { useErrorText } from '../../lib/errors';
import { formatMoney } from '../../lib/format';

// Stripe test mode payment methods (ADR 0008): no real card is ever charged.
const TEST_CARDS = ['pm_card_visa', 'pm_card_mastercard', 'pm_card_chargeDeclined'] as const;

interface BuyPassSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Buying a pass charges its price and the deposit (BR5). One Idempotency-Key per purchase attempt:
 * a retry after a timeout or a 503 reuses it, so the guest is never charged twice; a declined
 * card or a new attempt gets a fresh one, as the backend asks.
 */
export default function BuyPassSheet({ open, onOpenChange }: BuyPassSheetProps) {
  const { t, i18n } = useTranslation('guest');
  const prices = usePassPrices();
  const buy = useBuyPass();
  const errorText = useErrorText();
  const [passType, setPassType] = useState<PassType>('STAY');
  const [card, setCard] = useState<(typeof TEST_CARDS)[number]>('pm_card_visa');
  const attemptKey = useRef(newIdempotencyKey());
  const cardId = useId();
  const groupId = useId();

  useEffect(() => {
    if (open) {
      attemptKey.current = newIdempotencyKey();
      buy.reset();
    }
    // reset only when the sheet opens
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const selected = prices.data?.find((p) => p.passType === passType);
  const money = (cents: number, currency: string) => formatMoney(cents, currency, i18n.language);

  const submit = () => {
    buy.mutate(
      { passType, paymentMethod: card, idempotencyKey: attemptKey.current },
      {
        onSuccess: (pass) => {
          attemptKey.current = newIdempotencyKey();
          onOpenChange(false);
          toast.success(t('buy.bought', { pass: t(`passTypes.${pass.passType}.name`) }));
        },
        onError: (error) => {
          // Only an answer that may have charged nothing keeps the key for a safe retry.
          const retrySafe =
            error instanceof NetworkError || isProblem(error, 503) || isProblem(error, 409);
          if (!retrySafe) attemptKey.current = newIdempotencyKey();
        },
      },
    );
  };

  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={t('buy.title')}
      footer={
        <div className="buy-foot">
          {selected && (
            <p className="buy-total">
              <span>{t('buy.today')}</span>
              <span className="num">
                {money(selected.priceCents + selected.depositCents, selected.currency)}
              </span>
            </p>
          )}
          <Button
            variant="primary"
            size="lg"
            onClick={submit}
            busy={buy.isPending}
            disabled={buy.isPending || !selected}
          >
            {buy.isPending ? t('buy.paying') : t('buy.pay')}
          </Button>
        </div>
      }
    >
      <p id={groupId} className="visually-hidden">
        {t('buy.choose')}
      </p>
      <div role="radiogroup" aria-labelledby={groupId} className="choice-list">
        {prices.isPending &&
          [0, 1, 2].map((i) => (
            <div key={i} className="choice" aria-hidden="true">
              <span className="choice-main">
                <Skeleton width={120} height={14} />
              </span>
              <Skeleton width={56} height={14} />
            </div>
          ))}
        {prices.data?.map((price) => (
          <button
            key={price.passType}
            type="button"
            role="radio"
            aria-checked={price.passType === passType}
            className="choice sq"
            onClick={() => setPassType(price.passType)}
          >
            <span className="choice-main">
              <span className="choice-title">{t(`passTypes.${price.passType}.name`)}</span>
              <span className="choice-sub">{t(`passTypes.${price.passType}.rule`)}</span>
            </span>
            <span className="num choice-price">{money(price.priceCents, price.currency)}</span>
          </button>
        ))}
      </div>

      {selected && (
        <div className="buy-lines">
          <p className="buy-line">
            <span>{t('buy.deposit')}</span>
            <span className="num">{money(selected.depositCents, selected.currency)}</span>
          </p>
          <p className="buy-note">{t('buy.activation')}</p>
        </div>
      )}

      <div className="field buy-card">
        <label className="field-label" htmlFor={cardId}>
          {t('buy.card')}
        </label>
        <select
          id={cardId}
          className="input sq"
          value={card}
          onChange={(event) => setCard(event.target.value as typeof card)}
        >
          {TEST_CARDS.map((value) => (
            <option key={value} value={value}>
              {t(`buy.cards.${value}`)}
            </option>
          ))}
        </select>
      </div>

      {buy.isError && (
        <p role="alert" className="form-error">
          {errorText(buy.error, {
            '402': 'guest:buy.errors.declined',
            '403': 'guest:buy.errors.noStay',
            '409:Pass already open': 'guest:buy.errors.alreadyOpen',
            '409': 'guest:buy.errors.inProgress',
            '503': 'guest:buy.errors.retry',
            network: 'guest:buy.errors.retry',
          })}
        </p>
      )}
    </Sheet>
  );
}
