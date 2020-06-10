#!/bin/bash

set -ev

if [ "$TRAVIS_PULL_REQUEST" = "false" ]
then
    openssl aes-256-cbc -K $encrypted_6c4fc944fe71_key -iv $encrypted_6c4fc944fe71_iv -in .travis/secrets.tar.enc -out .travis/secrets.tar -d
    tar xvf .travis/secrets.tar
    cp google-services.json app/google-services.json
    cp google-services.json wear/google-services.json
    cp upload_keystore.keystore app/release_keystore.keystore
    cp upload_keystore.keystore wear/release_keystore.keystore
    cp home-assistant-mobile-apps-0b13292f44c4.json app/playStorePublishServiceCredentialsFile.json
    cp home-assistant-mobile-apps-0b13292f44c4.json app/playStorePublishServiceCredentialsFile.json
    mv home-assistant-mobile-apps-5fd6b9dd0fdb.json firebaseAppDistributionServiceCredentialsFile.json
    cp .travis/mock-google-services.json app/src/debug/google-services.json
    cp .travis/mock-google-services.json wear/src/debug/google-services.json
else
    cp .travis/mock-google-services.json app/google-services.json
    cp .travis/mock-google-services.json wear/google-services.json
fi
