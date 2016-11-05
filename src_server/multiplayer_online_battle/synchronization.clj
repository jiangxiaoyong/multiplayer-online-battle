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
  []
  (let [uids (:ws @ws/connected-uids)]
    (doseq [uid uids]
      (ws/send-fn uid
        [:game-lobby/all-players
         {:data @players}]))))
