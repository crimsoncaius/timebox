import { BrowserNotificationSettings, CheckInSettings } from '../activity/CheckInSettings'
import { FocusWakeSettings } from '../activity/FocusMode'
import { ReportingTimezoneSettings } from '../activity/ReportingTimezoneSettings'
import { Layout } from '../../components/Layout'
import { SettingsSection } from '../../components/SettingsControls'
import { ApiKeySetting, GoogleCalendar } from '../calendar/GoogleCalendar'

export function SettingsPage() {
  return (
    <Layout>
      <section className="mb-10 max-w-3xl sm:mb-12">
        <h1 className="mb-2 font-headline text-[2.75rem] font-extralight leading-none tracking-tighter text-on-surface">
          Settings
        </h1>

      </section>

      <div className="max-w-3xl space-y-6">
        <SettingsSection id="settings-google" title="Google Calendar" scope="shared"><GoogleCalendar /></SettingsSection>
        <SettingsSection
          id="settings-day"
          title="Day & time"
          scope="shared"
        >
          <ReportingTimezoneSettings />
        </SettingsSection>

        <SettingsSection id="settings-focus" title="Focus & check-ins" scope="device">
          <FocusWakeSettings />
          <CheckInSettings />
        </SettingsSection>

        <SettingsSection id="settings-notifications" title="Notifications" scope="device">
          <BrowserNotificationSettings />
        </SettingsSection>
        <SettingsSection id="settings-connection" title="Connection" scope="device"><ApiKeySetting /></SettingsSection>
      </div>
    </Layout>
  )
}
