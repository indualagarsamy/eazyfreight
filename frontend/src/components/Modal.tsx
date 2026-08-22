import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import styles from './Modal.module.css'

interface Props {
  title: string
  description?: string
  onClose: () => void
  children: ReactNode
  width?: number
}

export function Modal({ title, description, onClose, children, width = 520 }: Props) {
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    dialogRef.current?.querySelector<HTMLElement>(
      'input, select, textarea, button',
    )?.focus()
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div className={styles.backdrop} onMouseDown={onClose}>
      <div
        ref={dialogRef}
        className={styles.dialog}
        style={{ maxWidth: width }}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(event) => event.stopPropagation()}
      >
        <header className={styles.header}>
          <div>
            <h2 className={styles.title}>{title}</h2>
            {description && <p className={styles.description}>{description}</p>}
          </div>
          <button className={styles.close} onClick={onClose} aria-label="Close">&times;</button>
        </header>
        <div className={styles.body}>{children}</div>
      </div>
    </div>
  )
}
