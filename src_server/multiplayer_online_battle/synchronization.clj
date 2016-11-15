(ns multiplayer-online-battle.synchronization
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async  :refer (<! <!! >! >!! put! take! chan go go-loop timeout)]
            [multiplayer-online-battle.websocket :as ws]
            [taoensso.timbre    :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.game-state :refer [players]]))

;;;;;;;;;;;; Frame Synchronization for game lobby

(defn synchronize-game-lobby
  "server->client async pushes, setup a loop to broadcast all players status to all connected players 10 times per second"
  [ev-type & rest]
  (let [data (first rest)
        broadcast (fn [payload]
                    (let [uids (:ws @ws/connected-uids)]
                      (doseq [uid uids]
                        (ws/send-fn uid
                                    [ev-type
                                     {:payload payload}]))))
        count-down (fn []
                     (go
                       (doseq [count [3 2 1 0]]
                         (broadcast {:count count})
                         (<! (timeout 1000)))))]
    (cond
     (= ev-type :game-lobby/player-come) (broadcast data)
     (= ev-type :game-looby/player-leave) (broadcast data)
     (= ev-type :game-lobby/player-update) (broadcast data)
     (= ev-type :game-lobby/pre-enter-game-count-down) (count-down)
     (= ev-type :game-lobby/pre-enter-game-dest) (broadcast data)
     (= ev-type :game-lobby/all-players-ready) (broadcast data))))
