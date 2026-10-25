# Google Calendar setup

Calendar import uses one Google account for the deployment's shared dataset.
Both web and Android use the backend connection. Google owns event titles and
times; Timebox stores local classification, notes and optional Task linkage.
The accepted behavior is in [the specification](specs/google-calendar-import.md).

## Configure the backend

Run `uv sync` and `alembic upgrade head` from `backend` before starting the API.
Configure `API_KEY` and enter the same key in web Settings > Connection and
Android connection settings. Keep authenticated access enabled while imports
or Google data remain in the database.

In Google Cloud, enable Google Calendar API and configure an OAuth consent app.
For personal testing, use an external testing audience and add your Google
account as a test user. Create a Web application client whose authorized
redirect URI exactly matches the backend's `/google-calendar/callback` URL.
Use HTTPS for a deployed backend; loopback HTTP is for local review only.

Set these backend environment variables (never frontend variables):

- `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`: the web client credentials.
- `GOOGLE_REDIRECT_URI`: the exact registered callback URL.
- `GOOGLE_TOKEN_KEY`: a Fernet key, generated once with
  `python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"`.

Keep the token encryption key with your deployment secrets and backups. Changing
or losing it makes stored refresh tokens unreadable and requires reconnecting.
Do not commit secrets or log OAuth callback query parameters.

The consent flow requests only `calendar.calendarlist.readonly` and
`calendar.events.readonly`. It cannot modify Google events. In Timebox Settings,
connect Google, select calendars in priority order, and confirm the impact
preview. Until calendars are selected, no events are imported.

## Operation

The backend checks selected calendars every five minutes while running. Refresh
now and stale Day reads also check Google. Failed reads retain placed plans and
report the failure. Testing-mode authorization can require periodic reconnection;
follow the Google console's current audience and token-expiry guidance before
using the connection as a long-lived deployment.

Disconnect removes stored Google credentials and future imports after preview.
It preserves started plans, Actuals, Tasks and exclusion choices. Forget also
clears cached source details and choices, retaining only the identities needed
to prevent reimporting already-started history. Revoking the app in your Google
account is independent of removing its locally stored credentials.

The poller currently holds the existing exclusive writer admission while reading
Google. Large calendars or slow Google responses can delay application writes.
Multiple API processes serialize through that admission; use one API process for
this initial deployment to avoid redundant polling.
