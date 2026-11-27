# PiMsg subscriptions — Google Play setup

The app and API recognise these subscription product IDs. Product IDs are permanent in Google Play; create them exactly as written.

| Tier | Product ID | Billing period / base plan ID |
|---|---|---|
| Plus | `pimsg_plus_monthly` | Monthly / `monthly` |
| Plus | `pimsg_plus_yearly` | Annual / `yearly` |
| Pro | `pimsg_pro_weekly` | Weekly / `weekly` (existing) |
| Pro | `pimsg_pro_monthly` | Monthly / `monthly` (existing) |
| Pro | `pimsg_pro_yearly` | Annual / `yearly` |
| Business | `pimsg_business_monthly` | Monthly / `monthly` |
| Business | `pimsg_business_yearly` | Annual / `yearly` |

For each new product: create a subscription, add one auto-renewing base plan with the ID shown above, set the Angola price and activate both the base plan and subscription. Each product ID is queried separately by the Android app. Do not create additional base plans under these products until the app's offer selection is updated.

## Entitlements enforced by the Worker

| Feature | Free | Plus | Pro | Business |
|---|---:|---:|---:|---:|
| Contacts | 30 | Unlimited | Unlimited | Unlimited |
| Messages per day | 30 | Unlimited | Unlimited | Unlimited |
| Personalised link replies | 500/month | Unlimited | Unlimited | Unlimited |
| Translations per day | 4 | 25 | 75 | 150 |
| AI requests per day (all AI bots combined) | 0 | 5 | 20 | 50 |
| Active reminders | 0 | 20 | 100 | 500 |
| Custom bots | 0 | 0 | 5 | 20 |
| Pi Business workspace | No | No | No | Yes |
| Profile intro video | No | No | Yes | Yes |

Existing Pro weekly/monthly purchases remain Pro entitlements.

## Cost protection

- All inference now uses Cloudflare Workers AI `@cf/meta/llama-3.1-8b-instruct-fp8-fast`, not the more expensive 70B model.
- Output is capped at 450 tokens by default (translation: 512).
- A global cap of 2,000 inference requests per UTC day is enforced in D1 across all users and plans. Per-user limits reset using the user's configured timezone (Angola by default).
- Translation results are cached per user/message/language; cached results do not consume another translation or AI request.
- A failed inference releases the reserved per-user and global quota.
- These are request-count safeguards, not a guarantee of a fixed maximum invoice: input lengths and actual model usage vary. Monitor Workers AI usage and billing in Cloudflare.

## Required before real purchases can be activated

1. Create and activate the products/base plans above in Play Console.
2. Link a Google Play Developer API service account with access to this app.
3. Add its full JSON key as the Cloudflare Worker secret `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`.
4. Confirm the Worker is bound to the secret and that the package name is `com.pimsg.isabstudio`.
5. Test a purchase, restore, cancellation, expiry and account-switch scenario with Play license testers.

The Worker currently has no `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` secret binding. Therefore the catalogue can be displayed, but purchase verification will remain disabled until step 3 is completed. Never place the service-account JSON in GitHub or Android source code.
