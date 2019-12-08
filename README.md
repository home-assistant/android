# :iphone: Home Assistant Companion for Android  [![Build Status](https://travis-ci.com/home-assistant/home-assistant-android.svg?branch=master)](https://travis-ci.com/home-assistant/home-assistant-android)

## Setup Development Environment

- Download and install [Android Studio](https://developer.android.com/studio)

- Create a Firebase project at [Firebase Console](https://console.firebase.google.com)

- Add an Android app to your Firebase project, follow the on screen instruction download the `google-services.json`
  to your home-assistant-Android/app folder

- Use Android Studio open your source code folder and click Run -> Run 'app'

- Connect your phone or create a new virtual device following on screen instruction

- :tada:


## Continuous Integration

We are using [Travis](https://travis-ci.com/home-assistant/home-assistant-android) to perform continuous integration both by unit testing and deploying to [Firebase App Distribution](https://appdistribution.firebase.dev/i/8zf5W4zz) or [Play Store](https://play.google.com/store/apps/details?id=io.homeassistant.companion.android) when we add a git tag.

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

## Troubleshooting

The steps can vary depending on the specific device.

**Duplicate devices:**
- This issue is fixed with the upcoming release of Home Assistant version 0.103.

**Location is not updating:**
1. Make sure you have granted the Home Assistant app access to your location. (Settings - Apps - Permissions - Location)
2. Make sure battery optimization for the the Home Assistant app is **off**
