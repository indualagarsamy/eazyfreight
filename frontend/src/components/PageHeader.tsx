import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import styles from './PageHeader.module.css'

interface Props {
  title: ReactNode
  subtitle?: ReactNode
  backTo?: { to: string; label: string }
  actions?: ReactNode
  badges?: ReactNode
}

export function PageHeader({ title, subtitle, backTo, actions, badges }: Props) {
  return (
    <header className={styles.header}>
      {backTo && (
        <Link to={backTo.to} className={styles.back}>
          <span aria-hidden>←</span> {backTo.label}
        </Link>
      )}
      <div className={styles.row}>
        <div className={styles.titleBlock}>
          <div className={styles.titleLine}>
            <h1 className={styles.title}>{title}</h1>
            {badges}
          </div>
          {subtitle && <p className={styles.subtitle}>{subtitle}</p>}
        </div>
        {actions && <div className={styles.actions}>{actions}</div>}
      </div>
    </header>
  )
}
