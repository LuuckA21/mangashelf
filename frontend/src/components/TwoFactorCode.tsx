import { useI18n } from '../i18n'

export default function TwoFactorCode({
  id,
  value,
  onChange,
}: {
  id: string
  value: string
  onChange: (value: string) => void
}) {
  const { t } = useI18n()
  return (
    <div className="field">
      <label htmlFor={id}>{t('twoFactor.code')}</label>
      <input
        id={id}
        autoComplete="one-time-code"
        autoCapitalize="none"
        spellCheck={false}
        maxLength={64}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        required
        aria-describedby={`${id}-help`}
      />
      <p id={`${id}-help`} className="muted field-help">
        {t('twoFactor.codeHelp')}
      </p>
    </div>
  )
}
