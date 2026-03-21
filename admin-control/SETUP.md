# GIET ERP Control Service Setup

Use this Apps Script project to power:

- GitHub-based `app-control.json` updates
- Google Sheets user/install tracking
- Roll-number detail-view blocking
- Admin app control actions

## Google Sheet tabs

Create one Google Sheet with these sheets:

- `Users`
- `RollBlocks`
- `AuditLog`

The script auto-adds header rows if they are empty.

## Script Properties

Set these in **Apps Script → Project Settings → Script Properties**:

- `ADMIN_SECRET` → must match `BuildConfig.ADMIN_SHARED_SECRET` in `GietErpAdmin`
- `GITHUB_TOKEN` → GitHub token with repo content write access
- `GITHUB_OWNER` → e.g. `ziskare-world`
- `GITHUB_REPO` → e.g. `GIET-Erp-clone`
- `GITHUB_CONTROL_PATH` → `release-assets/app-control.json`
- `GITHUB_BRANCH` → `main`

## Deploy

1. Open `admin-control/Code.gs` in a Google Apps Script project bound to the sheet.
2. Save the project.
3. Deploy as **Web App**:
   - Execute as: **Me**
   - Who has access: **Anyone**
4. Copy the deployed Web App URL.

## Android app config

Update these placeholders:

- `GietErp2/app/src/main/res/values/strings.xml`
  - `apps_script_web_app_url`
- `GietErpAdmin/app/build.gradle.kts`
  - `APPS_SCRIPT_WEB_APP_URL`
  - `ADMIN_SHARED_SECRET`

## GitHub control file

The main app reads:

- `release-assets/app-control.json`

The admin app never writes GitHub directly. It calls Apps Script, and Apps Script updates the GitHub file through the GitHub API.
