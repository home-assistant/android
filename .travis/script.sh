#!/bin/bash

set -ev

git log --format=%B -n 1 $TRAVIS_COMMIT > CHANGES.md
mkdir -p app/src/main/play/release-notes/en-US/
cp CHANGES.md app/src/main/play/release-notes/en-US/default.txt

export VERSION_CODE=`git rev-list --count HEAD`
export VERSION_NAME=`git describe --tags $(git rev-list --tags --max-count=1)`

./gradlew test
./gradlew lint
./gradlew ktlintCheck

if [ "$TRAVIS_PULL_REQUEST" = "false" ]
then
    if [ -n "$TRAVIS_TAG" ]
    then
        ./gradlew publishReleaseBundle
    elif [ "$TRAVIS_BRANCH" = "deployTest" ]
    then
        #./gradlew assembleRelease appDistributionUploadRelease
        ./gradlew assembleRelease publishReleaseBundle
    fi
fi