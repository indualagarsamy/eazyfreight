import { useFilingSystem } from '../api/compliance'
import styles from './SimulationBanner.module.css'

/**
 * Standing notice that filings do not reach CBP.
 *
 * <p>An EEI filing is a report to the United States government. Nobody should be
 * able to look at a screen showing an ITN and believe one was made, so this is
 * driven by the service's own answer rather than a hard-coded string in the UI.
 */
export function SimulationBanner() {
  const { data } = useFilingSystem()

  if (!data?.simulated) {
    return null
  }

  return (
    <div className={styles.banner} role="note">
      <span className={styles.tag}>Simulated</span>
      <span>{data.notice}</span>
    </div>
  )
}
