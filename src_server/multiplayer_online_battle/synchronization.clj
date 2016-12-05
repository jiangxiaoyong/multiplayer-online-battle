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

(declare uids-no-sender)
(declare sync-ch)

(defn- no-sender [s-id]
  (memoize (fn [uids]
             (remove (fn [id]
                       (= s-id id)) uids))))

(defn- broadcast-no-sender [ev data]
  (let [uids (uids-no-sender (:ws @ws/connected-uids))
        ev-msg (utils/ev-msg ev data)
        ids-data (conj [] (vec uids) ev-msg)
        ch-out (chan)]
    (go
      (>! ch-out ids-data))
    ch-out))

(defn- broadcast-all [ev data]
  (let [uids (:ws @ws/connected-uids)
        ev-msg (utils/ev-msg ev data)
        ids-data (conj [] (vec uids) ev-msg)
        ch-out (chan)]
    (go
      (>! ch-out ids-data))
    ch-out))

(defn unknow []
  (let []))

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

(defn init-sync [uid]
  (def uids-no-sender (no-sender uid))
  (def sync-ch (chan))
  (def broadcast (partial sync-fn broadcast-no-sender))
  (def emit (partial sync-fn broadcast-all))
  (ev-msg-sink sync-ch))
