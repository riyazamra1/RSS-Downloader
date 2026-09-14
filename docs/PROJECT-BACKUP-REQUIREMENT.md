# RSS Downloader — Complete Repository Backup Requirement

## Requirement

The complete RSS Downloader repository must be recoverable before and after project changes.

## Backup scope

A complete backup must preserve the full project tree, including source code, UI, RSS Core, RAY, contracts, documentation, configuration, workflows, and project assets.

Do not include secrets, API keys, OAuth tokens, credentials, `.env` files, or other sensitive runtime credentials in repository backups.

## Backup architecture

GitHub remains the source of truth. Google Drive is the planned secondary disaster-recovery destination.

Planned automated flow:

`GitHub push/schedule → complete repository snapshot → Google Drive`

Backups should include both a complete project snapshot and Git history where practical.

## Safety rule

Before substantial repository modifications, preserve a recoverable snapshot of the current complete project state. Never intentionally overwrite or delete project files without a recoverable copy.

## Status

This document records the requirement. Google Drive automation is a separate follow-up implementation after the RSS Downloader project is complete.
