(ns multiplayer-online-battle.states
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [cljs.core.async :refer [<! >! put! take! chan close! timeout]]
            [multiplayer-online-battle.utils :refer [mount-dom]]))

(enable-console-print!)

(def components-state (r/atom {}))
(reset! components-state {:game-lobby {:player-come-animate "animated fadeInUp"
                                       :player-ready-label "label label-success"
                                       :player-unready-label "label label-default"
                                       :player-ready-animate "animated bounceIn"} 
                          :landing-pg {:animate "animated fadeInDown"}})

(def game-lobby-state (r/atom {}))
