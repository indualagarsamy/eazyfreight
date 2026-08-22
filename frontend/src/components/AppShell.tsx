import { NavLink, Outlet } from 'react-router-dom'
import { useAlertDashboard } from '../api/alerts'
import styles from './AppShell.module.css'

const NAV = [
  { to: '/quotes', label: 'Quotes' },
  { to: '/bookings', label: 'Bookings' },
  { to: '/logistics', label: 'Container & equipment' },
  { to: '/compliance', label: 'Compliance' },
  { to: '/documentation', label: 'Documentation' },
  { to: '/finance', label: 'Finance' },
  { to: '/alerts', label: 'Alerts' },
]

export function AppShell() {
  const { data: alerts } = useAlertDashboard()

  /**
   * Only the two severities that mean "act today" reach the sidebar. A badge counting
   * every open alert would sit permanently at some double-digit number and stop being
   * information.
   */
  const urgent = (alerts?.byCategory.CRITICAL ?? 0) + (alerts?.byCategory.HIGH ?? 0)

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
              {item.to === '/alerts' && urgent > 0 && (
                <span className={styles.navBadge} aria-label={`${urgent} urgent`}>
                  {urgent}
                </span>
              )}
            </NavLink>
          ))}
        </nav>

        <div className={styles.sidebarFoot}>
          <p className={styles.footLine}>Ocean Export</p>
          <p className={styles.footHint}>
            Quote, Booking, Container & Equipment, Export Compliance, Documentation,
            Finance and Alerts.
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
