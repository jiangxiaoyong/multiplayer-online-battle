# multiplayer-online-battle

## Overview

This is an online battle game, which build on top of flappy-bird and allows multiple gamers challenge eath other in real time.

* It has been deployed to heroKu. [let's play](https://thawing-reef-68533.herokuapp.com/) 

Note: At least two players are needed to start game, just opening two different browsers

* Here is [my post](https://jiangxiaoyong.github.io/portfolio/multiplayerOnlineBattle/) about this project, which includes project architecture, design and summary

## Setup

### Running instruction locally for development

Thanks to Emacs, CIDER, nREPL and Figwheel providing efficient interactive development environment that rocks, which enable REPL-driven development for both front-end and back-end.

### Back-end: 

* git clone this project 
* open main.clj in Emacs
* M-x to open cider-jack-in
* excecute (start) in repl


### Frond-end:

* M-x to open shell
* lein cljsbuild once
* lein figwheel landing

or

* leign figwheel game-lobby

or

* lein figwheel gaming


## Deployment

HeroKu was choosed as the deployment platform.
This github repo has been connected to HeroKu, and the app will be automatically deployed after each push change to master branch. 
'Procfile' file was used to explicitly declare what command should be executed to start this app

[HeroKu official guide](https://devcenter.heroku.com/articles/getting-started-with-clojure#introduction)

### Set up Heroku

login first:

    heroku login

initial set up:

    heroku create

deploy the app:

    git push heroku master

