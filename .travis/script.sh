#!/bin/bash

echo $TRAVIS_COMMIT_MESSAGE > CHANGES.md
export VERSION_CODE=`git rev-list --count HEAD`

if [ "$TRAVIS_PULL_REQUEST" = "false" ]
then
    if [ "$TRAVIS_BRANCH" = "master" ]
    then
        ./gradlew assembleRelease appDistributionUploadRelease

        if [ -n "$TRAVIS_TAG" ]
        then
            ./gradlew publishReleaseBundle
        fi
    fi
else
    ./gradlew testReleaseUnitTest
fi