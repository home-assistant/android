# :iphone: Home Assistant Companion for Android  ![Beta Deploy](https://github.com/home-assistant/android/workflows/Beta%20Deploy/badge.svg)

## Documentation
If you are looking for documentation around the companion applications check out the [Home Assistant Companion Documentation](https://companion.home-assistant.io/).  This will provide you instructions on using the applications.

## Setup App Development Environment

- Download and install [Android Studio](https://developer.android.com/studio)

- Download / clone this repository to a folder on your computer

- Create a Firebase project at [Firebase Console](https://console.firebase.google.com)

- Create four Android apps, with following package names 
 - `io.homeassistant.companion.android`
 - `io.homeassistant.companion.android.debug`
 - `io.homeassistant.companion.android.minimal`
 - `io.homeassistant.companion.android.minimal.debug`

- Now download the `google-services.json` file and put it in the _home-assistant-Android/app_ folder. This file contains the configuration of the whole project (all the four applications).

  [You can also use the mock services file instead of generating your own](/.github/mock-google-services.json)
  The file should contain client IDs for all packages listed above for debugging to work properly.  **If you do not generate your own file push notification will never work**

- Start Android Studio, open your source code folder and check if the Gradle build will be successful using Build/Make Module "App". You might have to install the right Android SDK via Tools/SDK Manager first.

- Create keystore containing keypair for debug application signing. In Android Studio: Menu/Build/Generate signed APK, then use a button to create new keystore. Remember the passwords and key alias

- Set environmental variables used in `app/build.gradle.kts`:
 - `KEYSTORE_PASSWORD`
 - `KEYSTORE_ALIAS`
 - `KEYSTORE_ALIAS_PASSWORD`
 
- Run the build using `gradlew build` from command line.

- If the build is successful, you can run the app by doing the following: click **Run** -> **Run 'app'**

- Connect your phone or create a new virtual device following on screen instruction

- :tada:

If you get stuck while setting up your own environment, you can ask questions in the **#devs_mobile_apps** channel on [Discord](https://discord.gg/c5DvZ4e).

### Push Notifications

If you want to work on push notifications or use a development build with push notifications please go the server side code [HERE](https://github.com/home-assistant/mobile-apps-fcm-push) and deploy it to your firebase project.  Once you have your androidV1 URL to the deployed service, exchange that for your local builds [PUSH_URL](https://github.com/home-assistant/android/blob/master/data/src/main/java/io/homeassistant/companion/android/data/integration/IntegrationRepositoryImpl.kt#L42).

## App Flavors

The Android app has both a full flavor that uses Google Play Services to offer features like location tracking and notifications. There is also a minimal flavor that does not require Google Play Services and can be found in the releases section. The minimal flavor does not have location tracking or notifications.

## Testing Dev Releases

We are using [Github Actions](https://github.com/home-assistant/android/actions) to perform continuous integration both by unit testing, deploying dev releases to [Play Store Beta](https://play.google.com/apps/testing/io.homeassistant.companion.android) and final releases to the [Play Store](https://play.google.com/store/apps/details?id=io.homeassistant.companion.android) when we release. To help test out a specific feature/fixes users can find the APK on the Actions page for each pull request, this debug APK can be installed side-by-side the production or beta builds.

## Quality

We are using [ktlint](https://ktlint.github.io/) as our linter.
You can run a check locally on your machine with:
```bash
./gradlew ktlintCheck
```
This commands runs on our CI to check if your PR passes all tests. So we strongly recommend running it before committing.

To run a check with an auto-format:
```bash
./gradlew ktlintFormat
```

## Translating
The project currently uses [lokalise](https://lokalise.com/public/145814835dd655bc5ab0d0.36753359/) to translate the application.  If you are interested in helping translate go to the link and click start translating!


## Generating a release to production
* Create a new release with the name and tag in the `{MAJOR}.{MINOR}.{PATCH}` format
* Steal the release notes from the latest beta build
* This will cause a tag to be added to the project and the `Production Deploy` Workflow will handle the rest
* Note: Only the first 500 characters of the release notes get set in the Google Play release
