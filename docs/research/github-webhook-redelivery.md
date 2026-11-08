# GitHub webhook redelivery

Research for #336, 2026-11-08. Based on GitHub's public documentation, read on that date. No webhooks were created, triggered, or redelivered.

## Finding

**GitHub does not retry a failed webhook delivery.** Its documentation says so directly: ["GitHub does not automatically redeliver failed deliveries."](https://docs.github.com/en/webhooks/using-webhooks/handling-failed-webhook-deliveries)

**There are two supported ways to redeliver one**, and both work only for deliveries from the [past 3 days](https://docs.github.com/en/webhooks/testing-and-troubleshooting-webhooks/redelivering-webhooks):

1. Click **Redeliver** on the delivery in the GitHub web interface.
2. Call the REST API. A script that does this on a schedule is GitHub's documented way to get automatic retries.

## What counts as a failure

GitHub [records a delivery as failed](https://docs.github.com/en/webhooks/using-webhooks/handling-failed-webhook-deliveries) if, for example, the server is down or takes longer than 10 seconds to respond. On a timeout, GitHub [terminates the connection](https://docs.github.com/en/webhooks/using-webhooks/best-practices-for-using-webhooks#respond-within-10-seconds). [Troubleshooting webhooks](https://docs.github.com/en/webhooks/testing-and-troubleshooting-webhooks/troubleshooting-webhooks) lists the failure reasons shown on a delivery.

## Web interface

[Redelivering webhooks](https://docs.github.com/en/webhooks/testing-and-troubleshooting-webhooks/redelivering-webhooks) gives the steps for each webhook type. In each case, open the webhook's recent deliveries, click the delivery GUID, then click **Redeliver**.

| Webhook type | Who can redeliver | Where recent deliveries are |
| --- | --- | --- |
| Repository | People with admin access to the repository | Repository **Settings** > **Webhooks** > the webhook > **Recent deliveries** |
| Organization | Organization owners | Organization **Settings** > **Webhooks** > the webhook > **Recent deliveries** |
| GitHub App | The app owner and app managers | **Developer settings** > **GitHub Apps** > **Edit** > **Advanced** |
| GitHub Marketplace | The app owner and app managers | Marketplace listing > **Manage listing** > **Webhook** |
| GitHub Sponsors | The owner of the sponsored account | **Your sponsors** > **Dashboard** > **Webhooks** > the webhook |

## REST API

Redelivery is a `POST` to the delivery's `attempts` endpoint, which returns `202 Accepted`.

| Webhook type | List deliveries | Redeliver |
| --- | --- | --- |
| [Repository](https://docs.github.com/en/rest/repos/webhooks#redeliver-a-delivery-for-a-repository-webhook) | `GET /repos/{owner}/{repo}/hooks/{hook_id}/deliveries` | `POST /repos/{owner}/{repo}/hooks/{hook_id}/deliveries/{delivery_id}/attempts` |
| [Organization](https://docs.github.com/en/rest/orgs/webhooks#redeliver-a-delivery-for-an-organization-webhook) | `GET /orgs/{org}/hooks/{hook_id}/deliveries` | `POST /orgs/{org}/hooks/{hook_id}/deliveries/{delivery_id}/attempts` |
| [GitHub App](https://docs.github.com/en/rest/apps/webhooks#redeliver-a-delivery-for-an-app-webhook) (requires a JWT) | `GET /app/hook/deliveries` | `POST /app/hook/deliveries/{delivery_id}/attempts` |

GitHub Marketplace and GitHub Sponsors webhooks [have no API endpoints for delivery data](https://docs.github.com/en/webhooks/using-webhooks/handling-failed-webhook-deliveries), so they can only be redelivered in the web interface.

## Automating retries

[GitHub's recommended approach](https://docs.github.com/en/webhooks/using-webhooks/handling-failed-webhook-deliveries) is a scheduled script that lists deliveries since its last run, finds those whose `status` is not `OK`, and redelivers them. GitHub publishes example scripts for [repository](https://docs.github.com/en/webhooks/using-webhooks/automatically-redelivering-failed-deliveries-for-a-repository-webhook), [organization](https://docs.github.com/en/webhooks/using-webhooks/automatically-redelivering-failed-deliveries-for-an-organization-webhook), and [GitHub App](https://docs.github.com/en/webhooks/using-webhooks/automatically-redelivering-failed-deliveries-for-a-github-app-webhook) webhooks. The repository example runs as a GitHub Actions workflow every 6 hours.

Three details matter when writing one:

- **Group attempts by `guid`.** The [example script](https://docs.github.com/en/webhooks/using-webhooks/automatically-redelivering-failed-deliveries-for-a-repository-webhook) notes that the GUID stays constant across redeliveries. It skips any GUID that already has a successful attempt, and redelivers the others once, which prevents duplicate redeliveries.
- **Make the receiver safe to run twice.** A redelivery [carries the same `X-GitHub-Delivery` header](https://docs.github.com/en/webhooks/using-webhooks/best-practices-for-using-webhooks#use-the-x-github-delivery-header) as the original. A receiver that rejects repeated values as replays will also reject legitimate redeliveries, so it has to tell the two apart. The delivery list's `redelivery` field marks these attempts on GitHub's side.
- **Retry well inside the 3-day window.** Older deliveries can no longer be redelivered, so the schedule must be much shorter than 3 days. If the receiver is down for more than 3 days, the earliest events from the outage can be lost.

The `status=failure` filter on the [list endpoint](https://docs.github.com/en/rest/repos/webhooks#list-deliveries-for-a-repository-webhook) covers only `status_code` values from 400 to 599. Timeouts and connection failures may not record a code in that range; the documentation does not say what they record. GitHub's example script avoids the question by checking `status` against `OK`, and a custom script should do the same.
