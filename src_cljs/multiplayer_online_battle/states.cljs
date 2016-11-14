(ns multiplayer-online-battle.states
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [cljs.core.async :refer [<! >! put! take! chan close! timeout]]
            [multiplayer-online-battle.utils :refer [mount-dom]]))

(enable-console-print!)

(def components-state (r/atom {}))
(reset! components-state {:game-lobby {:style {:player-come-animated "animated fadeInUp"
                                               :player-ready-span "label label-success"
                                               :player-ready-label "ready"
                                               :player-unready-span "label label-default"
                                               :player-unready-label "unready"
                                               :player-ready-animated "animated bounceIn"
                                               :btn-ready "not-active btn btn-lg btn-info btn-block animated flipInX"
                                               :btn-ready-label "Waiting..."
                                               :btn-ready-animated "fa fa-circle-o-notch fa-spin"
                                               :btn-unready "btn btn-success btn-lg btn-block"
                                               :btn-unready-label "Ready"
                                               }} 
                          :landing-pg {:animate "animated fadeInDown"
                                       :allow-in true}})

(def game-lobby-state (r/atom {}))
