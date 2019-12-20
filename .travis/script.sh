#!/bin/bash

set -ev

echo `git log --format=%B -n 1 $TRAVIS_COMMIT` > CHANGES.md
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
    elif [ "$TRAVIS_BRANCH" = "master" ]
    then
        ./gradlew assembleRelease appDistributionUploadRelease
    fi
fi