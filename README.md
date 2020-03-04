# :iphone: Home Assistant Companion for Android  [![Build Status](https://travis-ci.com/home-assistant/home-assistant-android.svg?branch=master)](https://travis-ci.com/home-assistant/home-assistant-android)

## Documentation
If you are looking for documentation around the companion applications check out the [Home Assistant Companion Documentation](https://companion.home-assistant.io/).  This will provide you instructions on using the applications.

## Setup Development Environment

- Download and install [Android Studio](https://developer.android.com/studio)

- Download / clone this repository to a folder on your computer

- Create a Firebase project at [Firebase Console](https://console.firebase.google.com)

- Create two Android apps, one with `io.homeassistant.companion.android` and one with `io.homeassistant.companion.android.debug` as package name

- Now download the `google-services.json` file and put it in the _home-assistant-Android/app_ folder

  [You can also use the mock services file instead of generating your own](/.travis/mock-google-services.json)
  The file should contain client IDs for `io.homeassistant.companion.android` and `io.homeassistant.companion.android.debug` for debugging to work properly

- Start Android Studio, open your source code folder and check if the Gradle build will be successful

- If the build is successful, you can run the app by doing the following: click **Run** -> **Run 'app'**

- Connect your phone or create a new virtual device following on screen instruction

- :tada:

If you get stuck while setting up your own environment, you can ask questions in the **#devs_apps** channel on [Discord](https://discord.gg/c5DvZ4e).

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
The project currently uses [lokalise](https://lokalise.com/public/145814835dd655bc5ab0d0.36753359/) to translate the application.  If you are interested in helping translate go to the link and click start translating!


## Generating a release to production
Edit the build number in `/app/build.gradle` to your desired version.  Be sure to leave `${vCode}`!!

```kotlin
def vName = "X.X.X-${vCode}"
```
Merge that into master and allow the build to complete and validate on the beta channel. (Deploy there automatic)

Once ready to move to production tag the master branch and travis will start the promotion from beta -> production.
