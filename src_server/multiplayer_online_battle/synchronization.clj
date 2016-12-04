(ns multiplayer-online-battle.synchronization
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async  :refer (<! <!! >! >!! put! take! chan go go-loop timeout)]
            [multiplayer-online-battle.websocket :as ws]
            [taoensso.timbre    :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.game-state :refer [players]]
            [multiplayer-online-battle.utils :as utils]))

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

(defn- uids-no-sender [s-id]
  (let [ids (:ws @ws/connected-uids)]
    (remove (fn [id]
              (= s-id id)) ids)))

(def m-uids-no-sender (memoize uids-no-sender))

(defn broadcast-no-sender [s-id ev data]
  (let [uids-no-sender (m-uids-no-sender s-id)
        ev-msg (utils/ev-msg ev data)
        ids-data (conj [] (vec uids-no-sender) ev-msg)
        ch-out (chan)]
    (go
      (>! ch-out ids-data))
    ch-out))

(defn broadcast-all [ev data]
  (let [uids (:ws @ws/connected-uids)
        ev-msg (utils/ev-msg ev data)
        ids-data (conj [] (vec uids) ev-msg)
        ch-out (chan)]
    (go
      (>! ch-out ids-data))
    ch-out))

(defn sync-fn
  ([f & args] 
   (let [ch (apply f args)]
     (go-loop []
       (let [data (<! ch)]
         (>! sync-ch data))
       (recur)))))

(defn count-down [ev data]
  (go
    (doseq [count data]
      (sync-fn broadcast-all ev {:count count})
      (<! (timeout 1000)))))

(defn- ev-msg-sink
  [ch]
  (go-loop []
    (let [[uids payload] (<! ch)]
      (utils/send-ev-msg uids payload))
    (recur)))

(defn init-sync []
  (def sync-ch (chan))
  (ev-msg-sink sync-ch))
