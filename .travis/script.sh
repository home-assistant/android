#!/bin/bash

set -ev

git log --format=%B -n 1 $TRAVIS_COMMIT > CHANGES.md
mkdir -p app/src/main/play/release-notes/en-US/
cp CHANGES.md app/src/main/play/release-notes/en-US/default.txt

export VERSION_CODE=`git rev-list --count HEAD`

./gradlew test
./gradlew lint
./gradlew ktlintCheck

if [ "$TRAVIS_PULL_REQUEST" = "false" ]
then
    if [ -n "$TRAVIS_TAG" ]
    then
        echo "Release already in Play Store Console"
    elif [ "$TRAVIS_BRANCH" = "master" ]
    then
        ./gradlew assembleRelease appDistributionUploadRelease publishReleaseBundle
    fi
fi