# multiplayer-online-battle

## Overview

This is an online battle game, which allows multiple gamers challenge eath other in real time. The game is build on top of flappy-bird.

## Setup

### Create a reagent based project
```
lein new figwheel hello-world -- --reagent ;; for a reagent based project 
```

## Deployment

HeroKu was choosed as the deployment platform.
This github repo has been connected to HeroKu, and the app will be automatically deployed after each push change to master branch
Procfile is to explicitly declare what command should be executed to start this app

[HeroKu official guide] (https://devcenter.heroku.com/articles/getting-started-with-clojure#introduction)

### Set up Heroku

login first:

    heroku login

initial set up:

    heroku create

deploy the app:

    git push heroku master

