(ns multiplayer-online-battle.states
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [cljs.core.async :refer [<! >! put! take! chan close! timeout]]))

(enable-console-print!)

;; UI components state

(def components-state (r/atom {}))
(reset! components-state {:game-lobby {:style {:player-come-animated "animated fadeInUp"
                                               :player-ready-span "label label-success"
                                               :player-ready-label "Ready"
                                               :player-unready-span "label label-default"
                                               :player-unready-label "Idle"
                                               :player-gaming-span "label label-info"
                                               :player-gaming-label "Gaming"
                                               :player-ready-animated "animated bounceIn"
                                               :btn-ready "not-active btn btn-lg btn-info btn-block animated flipInX"
                                               :btn-ready-label "Waiting..."
                                               :btn-ready-animated "fa fa-circle-o-notch fa-spin"
                                               :btn-unready "btn btn-success btn-lg btn-block"
                                               :btn-unready-label "Ready"
                                               }
                                       :player-status{:ready 0
                                                      :unready 1
                                                      :gaming 2}}
                          :landing-pg {:animate "animated fadeInDown"
                                       :allow-in true}})

;; Game lobby state

(def game-lobby-init-state {:all-players-ready false :chsk-ready? false})
(def game-lobby-state (r/atom game-lobby-init-state))

;; Flappy-bird state

(def start-game? (chan))

(def flap-starting-state {:flappy-y 300
                          :flappy-x 0
                          :start-time 0
                          :time-delta 0
                          :jump-start-time 0
                          :jump-step 0
                          :jump-count 0
                          :alive true})

(def world-staring-state {:all-players {}
                          :timer-running false
                          :players-loaded? false
                          :cur-time 0
                          :ground-pos 0
                          :pillar-list
                          [{:start-time 0
                            :start-pos-x 1200
                            :cur-x 1200
                            :gap-top 200}]})

(def world (r/atom world-staring-state))


