# :iphone: Home Assistant Companion for Android  [![Build Status](https://travis-ci.com/home-assistant/home-assistant-android.svg?branch=master)](https://travis-ci.com/home-assistant/home-assistant-android)

## Documentation
If you are looking for documentation around the companion applications check out the [Home Assistant Companion Documentation](https://companion.home-assistant.io/).  This will give you instructions on using the applications.

## Setup Development Environment

- Download and install [Android Studio](https://developer.android.com/studio)

- Create a Firebase project at [Firebase Console](https://console.firebase.google.com)

- Add an Android app to your Firebase project, follow the on screen instruction download the `google-services.json` to your home-assistant-Android/app folder.

  The file should contain client IDs for `io.homeassistant.companion.android` _and_ `io.homeassistant.companion.android.debug` for debugging to work properly.

  [You can use the mock services file instead of generating your own.](/.travis/mock-google-services.json)

- Use Android Studio open your source code folder and click Run -> Run 'app'

- Connect your phone or create a new virtual device following on screen instruction

- :tada:


## Testing Dev Releases

We are using [Travis](https://travis-ci.com/home-assistant/home-assistant-android) to perform continuous integration both by unit testing, deploying dev releases to [Play Store Beta](https://play.google.com/apps/testing/io.homeassistant.companion.android) and final releases to the [Play Store](https://play.google.com/store/apps/details?id=io.homeassistant.companion.android) when we release.

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
The project currently uses [lokalise](https://lokalise.com/public/145814835dd655bc5ab0d0.36753359/) to translate the application.  If you are interested in helping translate go the the link and click start translating!


## Generating a release to production
Edit the build number in `/app/build.gradle` to your desired version.  Be sure to leave `${vCode}`!!

```kotlin
def vName = "X.X.X-${vCode}"
```
Merge that into master and allow the build to complete and validate on the beta channel. (Deploy there automatic)

Once ready to move to production log into play store -> Release Management -> App Releases -> Beta -> Promote to Production
