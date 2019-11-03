#!/bin/bash

if [ "$TRAVIS_PULL_REQUEST" = "false" ]
then
    openssl aes-256-cbc -K $encrypted_a039e732823a_key -iv $encrypted_a039e732823a_iv -in travis/secrets.tar.enc -out travis/secrets.tar -d
    tar xvf travis/secrets.tar
    cp travis/google-services.json app/google-services.json
    cp travis/release_keystore.keystore release_keystore.keystore
    cp travis/home-assistant-mobile-apps-5fd6b9dd0fdb.json serviceCredentialsFile.json
fi