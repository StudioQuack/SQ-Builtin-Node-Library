# Built-In Node Library — Webhook Ingress Classification

This manifest classifies every ingress-capable node in `SQ-Builtin-Node-Library` (all `nodes/*/api_spec.json` packages declaring `"kind": "ingress"`, plus custom Java node `github`, and non-webhook custom protocol nodes `email` and `queue`) into three delivery categories.

## Summary Counts

| Group | Delivery & Auth Model | Node Count | Share | `webhook_key` & `:listen` / `:send_and_wait` |
|---|---|---:|---:|---|
| **Group A** | HTTP Push — URL Webhook Key + optional HMAC / Challenge | **167** | 69.9% | Yes (`webhook_key` + `filterRules` + rich `ingress` specs on flagship/handshake providers) |
| **Group B** | HTTP Push — Header Auth / Fixed or Configurable Secret Header | **31** | 13.0% | Yes (`webhook_key` + header secret + `filterRules` + `header_secret` verify) |
| **Group C** | Non-Webhook / Pull / Broker / WebSocket / Cloud Bus | **41** (+ 2 custom protocol nodes) | 17.2% | **No** (intentionally left without HTTP `webhook_key` or `:listen`) |
| **Total Audited** | `api_spec.json` (`kind: "ingress"`) + `github` | **239** | 100.0% | — |

### Hybrid Protocol Notes

- **`telegram` (Group B):** Supports both `setWebhook` (HTTPS push with `X-Telegram-Bot-Api-Secret-Token`, handled here via `:listen` + `webhook_key` + `secret_token`) and `getUpdates` (long-poll, not opened by the node).
- **`discord` (Group A):** Supports both HTTP Interactions webhooks (`Ed25519` signature + PING challenge, handled here via `:listen` / `:send_and_wait` + `webhook_key` + `public_key`) and Gateway WebSockets (not opened by the node).
- **`google-pubsub`, `gmail`, `google-forms`, `google-business-profile` (Group B):** Expose `:listen` + `webhook_key` strictly for **Cloud Pub/Sub HTTP Push** subscriptions (`Authorization` / custom header); pull consumption stays on normal HTTP operations (`subscription.pull`).
- **Custom Non-Webhook Protocol Nodes (Group C Reference):** `email` (IMAP pull / SMTP send, `EmailNode.java`) and `queue` (Redis / in-memory / broker queue, `QueueNode.java`) are non-webhook custom Java handlers and do not expose HTTP `webhook_key`.

---

## Group A — HTTP Push (URL Webhook Key + Optional HMAC / Challenge) (167 nodes)

| Node Key | Name | Category | Ingress Ops | Notes / Verification |
|---|---|---|---|---|
| `action-network` | Action Network | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `activecampaign` | ActiveCampaign | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `adyen` | Adyen | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `afterpay` | Afterpay | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `aftership` | AfterShip | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `aircall` | Aircall | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `airtable` | Airtable | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `asana` | Asana | Productivity & Collaboration | `listen` | Asana X-Hook-Secret header echo handshake + X-Hook-Signature HMAC-SHA256 |
| `attio` | Attio | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `autopilot` | Autopilot | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `basecamp` | Basecamp | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `beehiiv` | Beehiiv | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `bigcommerce` | BigCommerce | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `bitbucket` | Bitbucket | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `bold` | Bold Commerce | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `box` | Box | Files & Documents | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `braintree` | Braintree | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `brevo` | Brevo | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `brex` | Brex | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `calendly` | Calendly | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `campaign-monitor` | Campaign Monitor | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `chargebee` | Chargebee | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `circleci` | CircleCI | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `clickup` | ClickUp | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `clockify` | Clockify | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `close` | Close | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `cloudconvert` | CloudConvert | Files & Documents | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `confluence` | Confluence | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `constant-contact` | Constant Contact | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `copper` | Copper | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `customer-io` | Customer.io | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `dailymotion` | Dailymotion | Social Media | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `digitalocean` | DigitalOcean | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `discord` | Discord | Communication & Messaging | `send-and-wait, listen` | Hybrid · Discord HTTP Interactions Ed25519 + PING challenge + send-and-wait (Gateway WS not opened) |
| `discourse` | Discourse | Social Media | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `docker-hub` | Docker Hub | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `docparser` | Docparser | Files & Documents | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `docusign` | DocuSign | Files & Documents | `listen` | DocuSign Connect X-DocuSign-Signature-1 Base64 HMAC |
| `doppler` | Doppler | Security & Identity | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `drift` | Drift | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `drip` | Drip | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `dropbox` | Dropbox | Files & Documents | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `dropbox-sign` | Dropbox Sign | Files & Documents | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `easypost` | EasyPost | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `ebay` | eBay | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `emelia` | Emelia | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `etsy` | Etsy | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `facebook-pages` | Facebook Pages | Social Media | `trigger` | Meta Graph hub.challenge + X-Hub-Signature-256 HMAC |
| `figjam` | FigJam | Productivity & Collaboration | `webhook_listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `flickr` | Flickr | News & Reference | `trigger` | Flickr Push (WebSub) GET hub.challenge handshake |
| `folk` | Folk | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `formstack` | Formstack | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `freshbooks` | FreshBooks | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `getresponse` | GetResponse | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `github` | GitHub Search | Developer Tools | `listen` | Flagship (Custom Java) · X-Hub-Signature-256 HMAC + ping handshake + X-GitHub-Delivery |
| `gocardless` | GoCardless | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `gotowebinar` | GoToWebinar | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `gumroad` | Gumroad | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `heroku` | Heroku | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `highlevel` | HighLevel | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `hive` | Hive | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `hootsuite` | Hootsuite | Social Media | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `hotjar` | Hotjar | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `hubspot` | HubSpot | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `infisical` | Infisical | Security & Identity | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `insightly` | Insightly | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `intercom` | Intercom | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `invoice-ninja` | Invoice Ninja | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `jira` | Jira | Productivity & Collaboration | `listen` | Flagship · Jira Cloud X-Hub-Signature HMAC-SHA256 + issue correlation |
| `jotform` | Jotform | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `keap` | Keap | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `kick` | Kick | Social Media | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `kit` | Kit | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `klarna` | Klarna | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `klaviyo` | Klaviyo | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `launchdarkly` | LaunchDarkly | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `lemlist` | Lemlist | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `lemon-squeezy` | Lemon Squeezy | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `line` | LINE | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `linear` | Linear | Productivity & Collaboration | `listen` | Linear-Signature HMAC-SHA256 |
| `linkedin` | LinkedIn | Social Media | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `linkedin-ads` | LinkedIn Ads | CRM & Marketing | `trigger.lead` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `loop-returns` | Loop Returns | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `mailchimp` | Mailchimp | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `mailerlite` | MailerLite | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `mailgun` | Mailgun | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `mailjet` | Mailjet | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `mailparser` | Mailparser | Files & Documents | `webhook_listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `mandrill` | Mandrill | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `mautic` | Mautic | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `mercury` | Mercury | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `messagebird` | MessageBird | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `meta-ads` | Meta Ads | CRM & Marketing | `trigger.leadgen, trigger.listen` | Meta Leadgen hub.challenge + X-Hub-Signature-256 HMAC |
| `microsoft-onedrive` | Microsoft OneDrive | Productivity & Collaboration | `listen` | Microsoft Graph validationToken + clientState |
| `microsoft-outlook` | Microsoft Outlook | Communication & Messaging | `send-and-wait, listen` | Microsoft Graph validationToken + clientState + send-and-wait |
| `microsoft-teams` | Microsoft Teams | Communication & Messaging | `send-and-wait, listen` | Flagship · Graph validationToken handshake + clientState verify + send-and-wait |
| `mollie` | Mollie | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `monday` | monday | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `monday-com` | Monday.com | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `netlify` | Netlify | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `notion` | Notion | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `onenote` | OneNote | Productivity & Collaboration | `listen` | Microsoft Graph validationToken + clientState |
| `onfleet` | Onfleet | E-Commerce & Payments | `trigger` | Onfleet GET ?check=... query handshake |
| `paddle` | Paddle | Finance & Accounting | `trigger` | Paddle-Signature timestamped HMAC (ts=...;h1=...) |
| `pagerduty` | PagerDuty | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `pandadoc` | PandaDoc | Files & Documents | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `parseur` | Parseur | Files & Documents | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `paypal` | PayPal | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `pipedrive` | Pipedrive | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `plaid` | Plaid | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `postmark` | Postmark | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `printful` | Printful | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `printify` | Printify | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `quickbooks` | QuickBooks | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `ramp` | Ramp | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `recharge` | ReCharge | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `recurly` | Recurly | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `render` | Render | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `revolut-business` | Revolut Business | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `ringcentral` | RingCentral | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `salesforce` | Salesforce | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `sendcloud` | Sendcloud | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `sendgrid` | SendGrid | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `sentry` | Sentry | Developer Tools | `listen` | Sentry-Hook-Signature HMAC-SHA256 |
| `shippo` | Shippo | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `shipstation` | ShipStation | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `shopify` | Shopify | E-Commerce & Payments | `trigger` | Flagship · X-Shopify-Hmac-Sha256 Base64 HMAC |
| `signnow` | SignNow | Files & Documents | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `slack` | Slack | Communication & Messaging | `send-and-wait, listen` | Flagship · Slack url_verification + v0:ts:rawBody HMAC + send-and-wait |
| `smartsheet` | Smartsheet | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `sonarqube` | SonarQube | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `square` | Square | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `squarespace` | Squarespace Commerce | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `statuspage` | Statuspage | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `streak` | Streak | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `stripe` | Stripe | Finance & Accounting | `trigger` | Flagship · Stripe-Signature timestamped HMAC (t=...,v1=...) |
| `surveymonkey` | SurveyMonkey | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `tally` | Tally | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `teamwork` | Teamwork | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `threads` | Threads | Social Media | `trigger` | Meta Threads hub.challenge + X-Hub-Signature-256 HMAC |
| `tiktok` | TikTok | Social Media | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `tiktok-ads` | TikTok Ads | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `toggl` | Toggl | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `trello` | Trello | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `truelayer` | TrueLayer | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `twilio` | Twilio | Communication & Messaging | `listen` | Flagship · X-Twilio-Signature HMAC-SHA1 over URL + sorted form params |
| `twitch` | Twitch | Social Media | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `typeform` | Typeform | CRM & Marketing | `trigger.listen` | Typeform-Signature Base64 HMAC-SHA256 |
| `unbounce` | Unbounce | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `vercel` | Vercel | Developer Tools | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `vimeo` | Vimeo | Social Media | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `vonage` | Vonage | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `walmart` | Walmart Marketplace | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `wave` | Wave | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `webex` | Webex | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `webflow` | Webflow | CRM & Marketing | `trigger.form, trigger.listen, trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `wecom` | WeCom | Communication & Messaging | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `whatsapp` | WhatsApp | Communication & Messaging | `send-and-wait, listen` | Flagship · Meta GET hub.challenge + X-Hub-Signature-256 HMAC + batched messages + send-and-wait |
| `wise` | Wise | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `wix` | Wix Stores | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `woocommerce` | WooCommerce | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `wrike` | Wrike | Productivity & Collaboration | `listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `wufoo` | Wufoo | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `xero` | Xero | Finance & Accounting | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `yotpo` | Yotpo | E-Commerce & Payments | `trigger` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `zendesk-sell` | Zendesk Sell | CRM & Marketing | `trigger.listen` | HTTP push webhook URL (JSON/form pass-through + webhook_key) |
| `zoom` | Zoom | Communication & Messaging | `listen` | Zoom endpoint.url_validation HMAC challenge + x-zm-signature verification |

---

## Group B — HTTP Push (Header Auth / Fixed Secret Header) (31 nodes)

| Node Key | Name | Category | Ingress Ops | Header / Notes |
|---|---|---|---|---|
| `adobe-acrobat-sign` | Adobe Acrobat Sign | Files & Documents | `webhook_listen` | Group B · Adobe Sign X-AdobeSign-ClientId GET echo + header auth |
| `auth0` | Auth0 | Security & Identity | `listen` | Auth0 HTTP Custom Webhook Log Stream Authorization token |
| `azure-devops` | Azure DevOps | Developer Tools | `git_push_listen, listen` | Azure DevOps Service Hooks custom HTTP header auth |
| `braze` | Braze | CRM & Marketing | `trigger.listen` | Braze Webhook Campaigns / Currents HTTP custom auth header |
| `cloudflare` | Cloudflare | Developer Tools | `listen` | Cloudflare Notifications fixed cf-webhook-auth header |
| `datadog` | Datadog | Developer Tools | `listen` | Datadog Webhooks custom auth header |
| `elastic-security` | Elastic Security | Security & Identity | `listen` | Kibana / Elastic Security Webhook Connector custom auth header |
| `freshworks-crm` | Freshworks CRM | CRM & Marketing | `trigger.listen` | Freshsales Workflow Trigger Webhook custom auth header |
| `gitlab` | GitLab | Developer Tools | `listen` | Flagship (Group B) · X-Gitlab-Token header verification |
| `gmail` | Gmail | Communication & Messaging | `listen` | Hybrid: users.watch -> Cloud Pub/Sub HTTP Push subscription |
| `google-business-profile` | Google Business Profile | Social Media | `trigger` | Hybrid: Business Profile notifications -> Cloud Pub/Sub HTTP Push |
| `google-calendar` | Google Calendar | Productivity & Collaboration | `listen` | Google Calendar Push Notifications X-Goog-Channel-Token header |
| `google-chat` | Google Chat | Communication & Messaging | `send-and-wait, listen` | Google Chat HTTP App header auth + send-and-wait |
| `google-drive` | Google Drive | Productivity & Collaboration | `listen` | Google Drive Push Notifications X-Goog-Channel-Token header |
| `google-forms` | Google Forms | CRM & Marketing | `trigger.listen` | Hybrid: forms.watches.create -> Cloud Pub/Sub HTTP Push |
| `google-pubsub` | Google Pub/Sub | Databases & Storage | `trigger.listen` | Hybrid: Cloud Pub/Sub HTTP Push subscription (vs subscription.pull) |
| `instapage` | Instapage | CRM & Marketing | `trigger.listen` | Ingress-only Instapage form-submit webhook with optional Authorization header |
| `iterable` | Iterable | CRM & Marketing | `trigger.listen` | Iterable System / Journey Webhooks custom auth header |
| `jenkins` | Jenkins | Developer Tools | `listen` | Jenkins Notification / Generic Webhook custom header token |
| `marketo` | Marketo | CRM & Marketing | `trigger.listen` | Marketo Outbound Webhooks custom HTTP header auth |
| `mattermost` | Mattermost | Communication & Messaging | `listen` | Mattermost Outgoing Webhooks token verification |
| `microsoft-dynamics-365` | Microsoft Dynamics 365 | CRM & Marketing | `trigger.listen` | Dataverse Webhook Service Endpoint HttpHeader / WebhookKey auth |
| `new-relic` | New Relic | Developer Tools | `listen` | New Relic Alert Webhooks custom auth header |
| `okta` | Okta | Security & Identity | `listen` | Group B · Okta Event Hook GET verification handshake + Authorization header |
| `opsgenie` | Opsgenie | Developer Tools | `listen` | Opsgenie Webhook Integration custom HTTP headers |
| `rocket-chat` | Rocket.Chat | Communication & Messaging | `listen` | Rocket.Chat Outgoing Webhooks token header |
| `telegram` | Telegram | Communication & Messaging | `listen` | Flagship (Group B Hybrid) · setWebhook X-Telegram-Bot-Api-Secret-Token header auth |
| `thehive` | TheHive | Security & Identity | `listen` | TheHive Webhook Notifier custom Authorization / header |
| `zendesk-chat` | Zendesk Chat | Communication & Messaging | `listen` | Zendesk Sunshine Conversations X-API-Key header |
| `zoho-books` | Zoho Books | Finance & Accounting | `trigger` | Zoho Books Workflow Webhooks custom HTTP header |
| `zoho-crm` | Zoho CRM | CRM & Marketing | `trigger.listen` | Zoho CRM Notification Channels / Workflow Webhooks token header |

---

## Group C — Non-Webhook / Pull / Broker / WebSocket (41 `api_spec` nodes + 2 custom protocol nodes)

These nodes are **not** tagged with `:listen` or given an HTTP `webhook_key` parameter because their upstream protocol does not deliver native inbound SaaS HTTP push webhooks.

| Node Key | Name | Category | Spec Op(s) | Protocol / Rationale |
|---|---|---|---|---|
| `1password` | 1Password | Security & Identity | `listen` | Poll — 1Password Connect / Events API is polling-only; no outbound HTTP webhooks |
| `alpaca` | Alpaca | Finance & Accounting | `trigger` | WebSocket — Trading updates stream over WebSocket (trade_updates); no outbound HTTP webhooks |
| `amazon-s3` | Amazon S3 | Files & Documents | `listen` | Cloud Bus — S3 event notifications deliver via SQS / EventBridge / SNS intermediary |
| `amazon-ses` | Amazon SES | Communication & Messaging | `listen` | Cloud Bus — SES event publishing delivers via SNS / EventBridge / Firehose intermediary |
| `amazon-sp-api` | Amazon SP-API | E-Commerce & Payments | `trigger` | Cloud Bus — Amazon SP-API Notifications deliver to SQS or EventBridge only |
| `assemblyai` | AssemblyAI | AI & Machine Learning | `realtime` | WebSocket — realtime operation is Streaming v3 WebSocket STT, not an HTTP push webhook |
| `aws` | AWS | Developer Tools | `listen` | Cloud Bus — Generic AWS SigV4 API; events route via EventBridge / SQS / SNS |
| `aws-certificate-manager` | AWS Certificate Manager | Security & Identity | `listen` | Cloud Bus — ACM lifecycle events route via AWS EventBridge only |
| `aws-cognito` | AWS Cognito | Security & Identity | `listen` | Cloud Bus — Cognito triggers execute as AWS Lambda triggers only |
| `aws-iam` | AWS IAM | Security & Identity | `listen` | Cloud Bus — IAM audit events route via CloudTrail / EventBridge only |
| `aws-secrets-manager` | AWS Secrets Manager | Security & Identity | `listen` | Cloud Bus — Secret rotation triggers execute via AWS Lambda / EventBridge only |
| `aws-transcribe` | AWS Transcribe | AI & Machine Learning | `listen` | Cloud Bus — Transcription job status events route via AWS EventBridge only |
| `azure` | Azure | Developer Tools | `listen` | Cloud Bus — Azure Resource Manager / Service Bus control plane; events via Event Grid / Service Bus |
| `azure-key-vault` | Azure Key Vault | Security & Identity | `listen` | Cloud Bus — Key Vault events deliver via Azure Event Grid intermediary |
| `binance` | Binance | Finance & Accounting | `trigger` | WebSocket — Binance Spot market/user streams are WebSocket-only |
| `bitwarden` | Bitwarden | Security & Identity | `listen` | Poll — Bitwarden Public API is polling-only (list-events); no outbound HTTP webhooks |
| `coinbase` | Coinbase | Finance & Accounting | `trigger` | WebSocket — Coinbase Advanced Trade v3 real-time feeds are WebSocket-only |
| `couchdb` | CouchDB | Databases & Storage | `trigger.listen` | Long-Poll — CouchDB _changes feed is HTTP long-poll / continuous pull |
| `deepgram` | Deepgram | AI & Machine Learning | `listen` | WebSocket — listen operation is Live WebSocket STT, not an HTTP push webhook |
| `firebase` | Firebase | Databases & Storage | `trigger.listen` | Streaming / SDK — Firestore REST v1 has no native outbound HTTP webhooks |
| `gcp` | GCP | Developer Tools | `listen` | Cloud Bus — Generic GCP API control plane; events route via Eventarc / Cloud Pub/Sub |
| `gcp-secret-manager` | GCP Secret Manager | Security & Identity | `listen` | Cloud Bus — Secret Manager notifications publish to Cloud Pub/Sub intermediary |
| `gladia` | Gladia | AI & Machine Learning | `live` | WebSocket — live operation is Live WebSocket STT, not an HTTP push webhook |
| `google-docs` | Google Docs | Productivity & Collaboration | `listen` | Poll / Drive Watch — Google Docs API v1 has no native webhooks (use Google Drive watch or polling) |
| `hashicorp-vault` | HashiCorp Vault | Security & Identity | `listen` | WebSocket / Audit — Vault has no native outbound HTTP webhook push |
| `interactive-brokers` | Interactive Brokers | Finance & Accounting | `trigger` | WebSocket / Poll — IBKR Client Portal API uses WebSocket or polling only |
| `keycloak` | Keycloak | Security & Identity | `listen` | Poll / SPI — Keycloak core has no native HTTP webhook push without a custom SPI plugin |
| `kubernetes` | Kubernetes | Developer Tools | `listen` | Watch Stream — Kubernetes Watch API is a long-lived chunked stream, not a SaaS outbound webhook |
| `mastodon` | Mastodon | Social Media | `trigger` | SSE / Poll — Mastodon uses SSE streaming (/api/v1/streaming/user) or timeline polling |
| `matrix` | Matrix | Communication & Messaging | `listen` | Long-Poll — Matrix Client-Server API uses /sync long-poll |
| `miro` | Miro | Productivity & Collaboration | `listen` | No Official Surface — Miro shut down experimental board webhooks on 2025-12-05 |
| `moosend` | Moosend | CRM & Marketing | `trigger.listen` | Poll — Moosend REST v3 has no documented outbound webhook subscription endpoint |
| `ngrok` | ngrok | Developer Tools | `listen` | No Direct Webhook — ngrok Event Destinations route to external sinks or polling |
| `ntfy` | ntfy | Communication & Messaging | `listen` | SSE / WebSocket — ntfy subscriptions use SSE / JSON long-poll / WebSocket streams |
| `pardot` | Pardot | CRM & Marketing | `trigger.listen` | Poll — Account Engagement REST v5 has no native outbound webhook product |
| `pinterest` | Pinterest | Social Media | `trigger` | Poll — Pinterest API v5 has no official organic pin webhook (poll /v5/pins) |
| `pushbullet` | Pushbullet | Communication & Messaging | `listen` | WebSocket — Pushbullet push stream uses WebSocket tickles (wss://stream.pushbullet.com) |
| `rabbitmq` | RabbitMQ | Databases & Storage | `trigger.listen` | Broker — Message broker (AMQP / Management HTTP poll), not an inbound SaaS HTTP webhook |
| `rss` | RSS | News & Reference | `listen` | Poll — RSS 2.0 / Atom feeds are HTTP GET poll-only |
| `spotify` | Spotify | Social Media | `trigger` | Poll — Spotify Web API has no official outbound webhooks |
| `wordpress` | WordPress | Social Media | `trigger` | Poll — WordPress core wp/v2 REST has no official outbound webhooks (poll modified_after) |
| `email` | Email | Utilities & Core | `listen_imap` *(custom Java)* | IMAP pull — non-HTTP protocol |
| `queue` | Queue | Utilities & Core | `listen` *(custom Java)* | Broker / queue consumer — non-HTTP protocol |
