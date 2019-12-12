# :iphone: Home Assistant Companion for Android  [![Build Status](https://travis-ci.com/home-assistant/home-assistant-android.svg?branch=master)](https://travis-ci.com/home-assistant/home-assistant-android)

## Setup Development Environment

- Download and install [Android Studio](https://developer.android.com/studio)

- Create a Firebase project at [Firebase Console](https://console.firebase.google.com)

- Add an Android app to your Firebase project, follow the on screen instruction download the `google-services.json`
  to your home-assistant-Android/app folder

- Use Android Studio open your source code folder and click Run -> Run 'app'

- Connect your phone or create a new virtual device following on screen instruction

- :tada:


## Testing Dev Releases

We are using [Travis](https://travis-ci.com/home-assistant/home-assistant-android) to perform continuous integration both by unit testing, deploying dev releases to [Firebase App Distribution](https://appdistribution.firebase.dev/i/8zf5W4zz) and final releases to the [Play Store](https://play.google.com/store/apps/details?id=io.homeassistant.companion.android) when we add a git tag.

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
