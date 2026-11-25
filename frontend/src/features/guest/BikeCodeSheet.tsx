import { useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import Sheet from '../../components/ui/Sheet';
import { Button } from '../../components/ui/ui';

interface BikeCodeSheetProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onStart: (code: string) => void;
  starting: boolean;
}

// Starts a trip from the code painted on the bike, for a guest standing next to it.
export default function BikeCodeSheet({
  open,
  onOpenChange,
  onStart,
  starting,
}: BikeCodeSheetProps) {
  const { t } = useTranslation('guest');
  const [code, setCode] = useState('');
  const inputId = useId();

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (code.trim()) onStart(code.trim().toUpperCase());
  };

  return (
    <Sheet
      open={open}
      onOpenChange={onOpenChange}
      title={t('code.title')}
      description={t('code.description')}
    >
      <form className="code-form" onSubmit={submit}>
        <div className="field">
          <label className="field-label" htmlFor={inputId}>
            {t('code.label')}
          </label>
          <input
            id={inputId}
            className="input input-code code-input sq"
            value={code}
            onChange={(event) => setCode(event.target.value)}
            placeholder={t('code.placeholder')}
            autoCapitalize="characters"
            autoComplete="off"
            spellCheck={false}
            maxLength={32}
          />
        </div>
        <Button
          type="submit"
          variant="primary"
          size="lg"
          busy={starting}
          disabled={starting || !code.trim()}
        >
          {t('code.submit')}
        </Button>
      </form>
    </Sheet>
  );
}
