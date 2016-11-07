(ns multiplayer-online-battle.synchronization
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async  :refer (<! <!! >! >!! put! chan go go-loop)]
            [multiplayer-online-battle.websocket :as ws]
            [taoensso.timbre    :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.game-state :refer [players]]))

;;;;;;;;;;;; Frame Synchronization for game lobby

(defn synchronize-game-lobby
  "server->client async pushes, setup a loop to broadcast all players status to all connected players 10 times per second"
  [ev-type player]
  (let [broadcast (fn [payload]
                    (let [uids (:ws @ws/connected-uids)]
                      (doseq [uid uids]
                        (ws/send-fn uid
                                    [ev-type
                                     {:payload payload}]))))]
    (cond
     (= ev-type :game-lobby/player-come) (broadcast player)
     (= ev-type :game-looby/player-leave) (broadcast player)
     (= ev-type :game-lobby/player-update) (broadcast player))))
