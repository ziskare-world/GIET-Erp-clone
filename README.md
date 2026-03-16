# GIET-Erp-clone

Android student ERP app clone with attendance, marks, special student search, and in-app update support.

## Attendance notifications

The app now includes Android-side support for attendance change notifications using Firebase Cloud Messaging.

To enable it end-to-end:

1. Place a valid `google-services.json` file at `app/google-services.json`.
2. Set backend token endpoints in `app/src/main/res/values/strings.xml`:
   - `attendance_token_register_url`
   - `attendance_token_unregister_url`
3. Send FCM data payloads with:
   - `type=attendance_update`
   - `eventId`
   - `rollNo`
   - `subject`
   - `status`
   - optional `attendanceDate`, `semester`, `updatedAt`

Tapping the notification opens the app, refreshes attendance, and routes the user into the attendance report screen.

## AdMob dashboard banner

The dashboard now supports an anchored adaptive AdMob banner at the bottom of the screen with Google UMP consent handling.

Configured IDs:

- App ID: `ca-app-pub-5619760951459090~8317918493`
- Debug banner unit: Google adaptive banner test unit
- Release banner unit: `ca-app-pub-5619760951459090/1399957246`
