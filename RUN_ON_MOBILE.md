# Run App On Mobile

Use one command to build, install, and launch on a connected Android phone:

```powershell
.\run-on-mobile.ps1
```

Or:

```bat
run-on-mobile.bat
```

Options:

- Select a specific device:
  - `.\run-on-mobile.ps1 -DeviceId <device-id>`
- Skip build and only install/launch existing debug APK:
  - `.\run-on-mobile.ps1 -SkipBuild`
- Print commands without executing:
  - `.\run-on-mobile.ps1 -DryRun`
