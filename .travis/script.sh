#!/bin/bash

set -ev

echo $TRAVIS_COMMIT_MESSAGE > CHANGES.md
export VERSION_CODE=`git rev-list --count HEAD`

./gradlew test
./gradlew lint

if [ "$TRAVIS_PULL_REQUEST" = "false" ]
then
    if [ -n "$TRAVIS_TAG" ]
    then
        ./gradlew publishReleaseBundle
    elif [ "$TRAVIS_BRANCH" = "master" ]
    then
        ./gradlew assembleRelease appDistributionUploadRelease
    fi
fi