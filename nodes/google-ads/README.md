# Google Ads

Handler: `OfficialApiNode`. Docs: https://developers.google.com/google-ads/api/rest/reference/rest

Headers `developer-token` and `login-customer-id` come from config. GAQL lives in `query`; `campaign.get` has no Get RPC, so filter by id in the query (`... FROM campaign WHERE campaign.id = 1234567890`). Mutates take official `operations[]`; conversion upload takes official `conversions[]`.

Errors raise a BPMN error for the error boundary event (INVALID_INPUT, AUTH_FAILED, RATE_LIMITED, HTTP_<status>, REQUEST_FAILED); secrets are redacted.
