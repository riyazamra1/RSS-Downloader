# Google Drive Automatic Repository Backup

RSS Downloader now includes a GitHub Actions workflow that creates and uploads a complete repository backup to Google Drive.

## What is backed up

1. A ZIP snapshot of the complete tracked project at the exact Git commit.
2. A Git bundle containing the repository history and refs available to the workflow runner.
3. A small manifest containing the commit and backup timestamp.

Secrets and credentials are not copied into the backup by the workflow.

## Schedule

- Every push to `main`.
- Once daily at 02:17 UTC.
- Manual execution through GitHub Actions (`workflow_dispatch`).

## Required GitHub Actions secrets

Configure these repository secrets before the first backup run:

- `GOOGLE_DRIVE_CLIENT_ID`
- `GOOGLE_DRIVE_CLIENT_SECRET`
- `GOOGLE_DRIVE_REFRESH_TOKEN`
- `GOOGLE_DRIVE_FOLDER_ID`

The refresh token must belong to a Google account authorized for Google Drive and have Drive access sufficient to create files in the selected backup folder.

## Security

Never commit OAuth client secrets or refresh tokens into the repository. Store them only in GitHub Actions Secrets.

## Recovery model

GitHub remains the source of truth. Google Drive is the secondary disaster-recovery copy. The ZIP restores the project files; the Git bundle can restore Git history.

## Current status

The automation workflow is committed. The Google OAuth/Drive secrets still need to be configured in the GitHub repository before an upload can succeed.
