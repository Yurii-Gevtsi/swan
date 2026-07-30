@echo off
setlocal

where firebase.cmd >nul 2>nul
if errorlevel 1 (
  echo Firebase CLI is not installed. Run: npm.cmd install --global firebase-tools
  exit /b 1
)

firebase.cmd deploy --only hosting
