import { useId } from 'react'
import styles from './GatedButton.module.css'

interface Props {
  label: string
  /** Why the action is unavailable, or null when it is allowed. */
  reason: string | null
  onClick: () => void
  variant?: string
  size?: 'sm' | 'md'
}

/**
 * An action the domain may currently refuse.
 *
 * The reason is a mouse tooltip *and* an accessible description. It deliberately
 * is not the accessible name — `title` alone would replace "Send to customer" with
 * "Price the quotation first" for anyone using a screen reader, which loses what
 * the button actually does.
 */
export function GatedButton({ label, reason, onClick, variant = '', size = 'md' }: Props) {
  const describedById = useId()
  const disabled = reason !== null

  return (
    <>
      <button
        className={`btn ${size === 'sm' ? 'btn-sm' : ''} ${variant}`}
        disabled={disabled}
        title={reason ?? undefined}
        aria-label={label}
        aria-describedby={disabled ? describedById : undefined}
        onClick={onClick}
      >
        {label}
      </button>
      {disabled && (
        <span id={describedById} className={styles.srOnly}>
          Unavailable: {reason}
        </span>
      )}
    </>
  )
}
