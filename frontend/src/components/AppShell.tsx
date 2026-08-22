import { NavLink, Outlet } from 'react-router-dom'
import styles from './AppShell.module.css'

const NAV = [
  { to: '/quotes', label: 'Quotes' },
  { to: '/bookings', label: 'Bookings' },
  { to: '/logistics', label: 'Container & equipment' },
  { to: '/compliance', label: 'Compliance' },
]

export function AppShell() {
  return (
    <div className={styles.shell}>
      <aside className={styles.sidebar}>
        <div className={styles.brand}>
          <span className={styles.mark} aria-hidden>EF</span>
          <span className={styles.brandName}>Eazy Freight</span>
        </div>

        <nav className={styles.nav} aria-label="Main">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => `${styles.navLink} ${isActive ? styles.active : ''}`}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className={styles.sidebarFoot}>
          <p className={styles.footLine}>Ocean Export</p>
          <p className={styles.footHint}>
            Quote, Booking, Container & Equipment and Export Compliance. Documentation, Finance and Alerts are still to come.
          </p>
        </div>
      </aside>

      <div className={styles.main}>
        <header className={styles.topbar}>
          <span className={styles.env}>Local</span>
          <div className="spacer" />
          <div className={styles.user}>
            <span className={styles.avatar} aria-hidden>JB</span>
            <span className={styles.userName}>ops.jane</span>
          </div>
        </header>

        <main className={styles.content}>
          <Outlet />
        </main>
      </div>
    </div>
  )
}
