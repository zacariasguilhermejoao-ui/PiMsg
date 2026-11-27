# PiMsg — Compliance readiness checklist

**Internal working document — 27 November 2026**

This checklist is an operational preparation document, not a legal opinion or a certification of compliance. The public-facing policies are drafts and must be reviewed by qualified counsel in Angola and in each additional market where PiMsg is offered.

## Public documents added

- `/terms` — Terms of Use
- `/privacy` — Privacy Policy
- `/subscriptions` — Subscription Policy
- `/refund` — Refund Policy
- `/community` — Community Rules
- `/cookies` — Cookies and Similar Technologies Policy
- `/security` — Security and Vulnerability Reporting
- `/faq` — Frequently Asked Questions
- `/support` — Support contacts and FAQ entry point
- `/delete-account` — External account-deletion request page

The Android Settings screen links to these documents. The policies disclose that messages are not end-to-end encrypted in the current release and that phone numbers entered as contact details are not SMS-verified.

## Blocking work before a public launch

### 1. Identify the legal operator

Obtain and publish the exact registered legal name of the operator, commercial registration details, NIF, registered address, and a monitored support contact. “ISAB Group” is currently used as the brand name; do not treat it as the full registered legal identity unless the registration documents confirm that.

### 2. Angola personal-data compliance

Have Angolan counsel assess the application of Law No. 22/11 of 17 June (Personal Data Protection Law), including:
- whether the processing must be notified to or authorised by the Agência de Protecção de Dados (APD);
- the legal basis for each processing purpose and any required express consent;
- whether international transfers to Google and Cloudflare require additional safeguards, notifications, or authorisation;
- the required security documentation, access controls, retention schedule, and rights-handling process;
- the role and contact details of the data controller and any required privacy representative.

Do not assume that publishing a privacy policy alone satisfies the statutory requirements.

### 3. Account deletion — implementation gap

The current app and external page can create a pending deletion request in D1. The Worker does **not yet implement a complete automated deletion workflow** that removes the Firebase identity, user records, messages, media objects in R2, group ownership references, and other linked data. A pending request is not the same as completed deletion.

Before launch:
- implement identity verification for app and web requests;
- process requests within a published, legally reviewed deadline;
- delete or anonymise all eligible personal data across D1, R2, Firebase and other systems;
- retain only narrowly justified records required by law, fraud prevention or dispute resolution;
- notify the user when the request is completed or explain a lawful reason for retention;
- add a cancellation path only if the backend actually supports it;
- test deletion against every foreign-key relationship and media object;
- complete the Google Play Data deletion declarations and provide the external deletion URL.

### 4. Terms acceptance and version history — implementation gap

The app currently displays links and an acceptance statement, but a durable, versioned record of the user's acceptance is not yet wired to account creation. The D1 schema has a `legal_acceptances` table, but no confirmed API flow records the current terms/privacy versions.

Before launch:
- add explicit, unticked acceptance controls where legally required;
- record user ID, document type, version, timestamp and app version;
- require renewed acceptance for material changes when appropriate;
- do not bundle optional marketing consent with acceptance of essential terms.

### 5. Subscriptions and payments

- Subscription purchase actions are gated by the server-reported verified-email state.
- The Worker verifies the Firebase token claims and requires `email_verified`.
- The D1 subscription table now accepts Plus, Pro and Business plan values.
- The Google Play service-account secret `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` was previously absent from the Worker. Until it is securely configured and purchase verification is tested end-to-end, do not advertise paid subscriptions as ready for production.
- Create and activate every exact product ID and base plan in Play Console.
- Test purchase, acknowledgement, renewal, cancellation, grace period, on-hold, refund and restore flows using Play test accounts.
- Show price, currency, billing period, auto-renewal and cancellation instructions before purchase.
- Verify that no user can start a Play purchase with an unverified email.

### 6. Privacy and security operations

- Maintain a data inventory and purpose/legal-basis register.
- Define retention periods for messages, stories, support tickets, reports, moderation records, billing records, logs and backups.
- Publish a real support and privacy contact and monitor it.
- Define an incident-response procedure, escalation owner, evidence preservation, user/regulator notification assessment and incident register.
- Restrict staff access to message content and production data; log administrative access.
- Review Cloudflare and Google processor terms, access scopes, security settings and subprocessors.
- Confirm whether Cloudflare security challenges, analytics or other site components set cookies; update the cookie notice and consent controls if non-essential cookies are used.
- Perform a security review of account recovery, authentication, API authorisation, media access, abuse reporting and rate limits.

### 7. Consumer protection and commercial disclosures

Review the app and all marketing against Angola's Consumer Protection Law No. 15/03, including clear Portuguese information about service features, limitations, prices, recurring charges, support, complaint handling and refunds. Ensure marketing promises match the actual product and plan limits.

### 8. Android permissions and Google Play disclosures

Current permissions should be requested only when a feature needs them:
- Microphone: Android runtime request when the user starts recording a voice message.
- Notifications: native runtime request on Android 13+ when push notifications are enabled.
- Photos/files: Android system picker is used, so broad gallery access is not needed.
- Camera: removed from the manifest because the current app flow does not use direct camera capture.

Keep the Play Console Data Safety form, privacy policy, permission declarations and actual SDK/data behaviour aligned. Re-check after adding any new SDK or feature.

### 9. International availability

The public legal pages are currently in Portuguese. Before actively marketing in English, Spanish or French markets, translate and review the terms, privacy, subscription, refund and safety documents for those jurisdictions. The applicable consumer, privacy, child-safety and platform requirements may differ by country.

## Legal references to review

- Angola — Law No. 22/11 of 17 June, Personal Data Protection Law: https://apd.ao/fotos/frontend_1/editor2/110617_lei_22-11_de_17_junho-proteccao_dados_pessoais.pdf
- Angola — Law No. 15/03 of 22 July, Consumer Protection: https://inacom.gov.ao/wp-content/uploads/2025/06/lei_15_03_lei_de_defesa_do_consumidor-22_de_julho_de_2003-1.pdf
- Google Play — User Data and Privacy Policy: https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play — Account deletion requirements: https://support.google.com/googleplay/android-developer/answer/13327111

## Release decision

**Not yet compliance-ready for public launch.** The public policy pages and subscription email gate are in place, but account deletion processing, legal-acceptance records, verified operator details, APD assessment, processor/transfer review, incident response, and production billing configuration remain to be completed and tested.
