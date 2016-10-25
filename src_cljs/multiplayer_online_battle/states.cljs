(ns multiplayer-online-battle.states
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [cljs.core.async :refer [<! >! put! take! chan close! timeout]]
            [multiplayer-online-battle.utils :refer [mount-dom]]))

(enable-console-print!)

(def components-state (r/atom {}))
(reset! components-state {:game-loby {:visibility "" :animate "animated fadeInDown"} 
                         :landing-pg {:visibility "" :animate "animated fadeInDown"}})

