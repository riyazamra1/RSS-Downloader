# RSS Downloader

## RESET BASELINE — 2026-09-28

RSS Downloader has been intentionally reset to **0% implementation status**.

The previous UI/feature implementation is no longer treated as completed work. Existing code may remain in history for reference, but no previous feature is counted as complete.

### Rebuild order

1. **RSS KIT UI foundation**
   - Project logo and app identity
   - Splash → Registration → Welcome → App Features → Main App
   - Light / Dark / System
   - Typography, spacing, surfaces, icons, animation and responsive layout
   - Clean slide navigation
   - Settings / About / Contact / Privacy / Terms

2. **RSS Core account**
   - Email registration
   - Google/Gmail sign-in
   - Mandatory Terms acceptance
   - Verification and 24-hour expiry
   - Welcome / Welcome Back email behavior
   - Persistent RSS Core account/session
   - Cloud and multi-device account state

3. **RSS Core ↔ RAY**
   - RSS Core is the control plane
   - RAY is the server-side execution worker
   - Android never receives RAY secrets
   - Every endpoint is live-tested before being marked complete

4. **Downloader**
   - Social Downloader
   - Tamil Movies
   - Tamil Dubbed Movies
   - Search inside movie tabs
   - URL clipboard detection
   - Analysis → media options → authorized download
   - Progress, queue, cancellation and device save

5. **Premium**
   - RSS Core entitlement
   - Payments.lk target
   - No initial Play Billing implementation

### Source of truth

- Project repository: riyazamra1/RSS-Downloader
- RSS KIT: riyazamra1/RSS-Brand-Kit
- RSS Core: https://rsscore.cv
- RAY: server-side behind RSS Core

### Status policy

No build, deployment, test, API connection, email delivery, or feature is considered successful until it is actually verified.

**Current rebuild status: 0%.**
