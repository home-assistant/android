#!/bin/bash

set -ev

if [ "$TRAVIS_PULL_REQUEST" != "false" ]
then

    echo "Building PR"
    ./gradlew test
    ./gradlew lint
    ./gradlew ktlintCheck

elif [ -n "$TRAVIS_TAG" ]
then

    echo "Promoting Beta to Production"
    mkdir -p app/src/main/play/release-notes/en-US/
    previous=`git tag -l --sort=-creatordate | head -n 2 | tail -n 1`
    current=`git tag -l --sort=-creatordate | head -n 1`
    echo "Full patch change log: https://github.com/home-assistant/home-assistant-android/compare/${previous}...${current}" > app/src/main/play/release-notes/en-US/default.txt
    ./gradlew promoteArtifact --from-track beta --promote-track production

elif [ "$TRAVIS_BRANCH" = "master" ]
then

    echo "Building Master"
    mkdir -p app/src/main/play/release-notes/en-US/
    git log --format=%s | head -n 1 > app/src/main/play/release-notes/en-US/default.txt
    previous=`git tag -l --sort=-creatordate | head -n 1`
    echo "Diff from production: https://github.com/home-assistant/home-assistant-android/compare/${previous}...master" >> app/src/main/play/release-notes/en-US/default.txt

    export VERSION_CODE=`git rev-list --count HEAD`

    ./gradlew test
    ./gradlew lint
    ./gradlew ktlintCheck

    ./gradlew assembleRelease appDistributionUploadRelease publishReleaseBundle
fi
