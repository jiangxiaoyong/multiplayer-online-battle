(ns multiplayer-online-battle.control
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan sliding-buffer put! close! timeout]]
            [multiplayer-online-battle.states :refer [world]]))
