## Summary

This PR implements the `command_screen_off` notification command, allowing users to remotely lock their Android device screen via Home Assistant notifications.

### Motivation
Users requested the ability to lock their device screen remotely for security purposes (e.g., when leaving device unattended, emergency situations, parental controls), and power saving.

### Impact
- Adds new `command_screen_off` notification command
- Requires Device Administrator permission (one-time user approval)
- Follows existing command patterns in the codebase
- No breaking changes to existing functionality

## Implementation Details

### Files Modified/Created:
1. **MessagingManager.kt** - Added command constant, handling logic, permission checks, and user permission request dialog
2. **ScreenOffAdminReceiver.kt** (new) - Minimal DeviceAdminReceiver implementation
3. **device_admin.xml** (new) - Device admin policy definition for force-lock capability
4. **AndroidManifest.xml** - Registered DeviceAdminReceiver with proper intent filters and metadata
5. **strings.xml** - Added user-facing explanation for device admin permission

### How It Works:
1. User sends notification with `message: "command_screen_off"` from Home Assistant
2. App checks if Device Administrator permission is granted
3. If granted: immediately locks device screen using `DevicePolicyManager.lockNow()`
4. If not granted: shows explanation dialog and guides user to permission settings
5. Includes comprehensive logging for debugging

### Usage Example:
```yaml
service: notify.mobile_app_<your_device_id_here>
data:
  message: "command_screen_off"
```

## Checklist

- [x] The code follows the project's [code style](https://developers.home-assistant.io/docs/android/codestyle) and [best_practices](https://developers.home-assistant.io/docs/android/best_practices).
- [x] The changes have been thoroughly tested, and edge cases have been considered.
- [x] Changes are backward compatible whenever feasible. Any breaking changes are documented in the changelog for users and/or in the code for developers depending on the relevance.
- [ ] New or updated tests have been added to cover the changes following the testing [guidelines](https://developers.home-assistant.io/docs/android/testing/introduction).
  - Note: MessagingManager currently has no unit tests. This feature follows the exact same pattern as existing device commands (command_ble_transmitter, command_bluetooth, etc.). Testing requires extensive Android system mocking (DevicePolicyManager, Context, AlertDialog) that is not currently in place for this class.

## Screenshots

N/A - This is a backend notification command with no UI changes to the main app interface. The only UI element is the standard Android Device Administrator permission request dialog (system-provided).

## Link to pull request in documentation repositories

User Documentation: home-assistant/companion.home-assistant#[TO_BE_CREATED]

The documentation should include:
- Description of the `command_screen_off` notification command
- Required Device Administrator permission and how to grant it
- Usage examples
- Security considerations
- Troubleshooting (permission denied, logging guidance)

## Any other notes

- This command requires Device Administrator permission, which is a sensitive permission. Users will see a system dialog explaining the permission and must explicitly approve it.
- The implementation includes clear user messaging explaining why this permission is needed.
- Follows the same architectural patterns as other device commands in the codebase.
- Comprehensive Timber logging added for debugging purposes.
- The feature is non-breaking and opt-in (only activates when the specific command is sent).
