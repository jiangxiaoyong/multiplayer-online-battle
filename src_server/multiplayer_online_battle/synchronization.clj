(ns multiplayer-online-battle.synchronization
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async  :refer (<! <!! >! >!! put! take! poll! pipe chan go go-loop timeout close!)]
            [multiplayer-online-battle.websocket :as ws]
            [taoensso.timbre    :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.game-state :refer [players reset-game]]
            [multiplayer-online-battle.utils :as utils]))

;;;;;;;;;;;; Frame Synchronization for game lobby

(def cmd-msg-buffer (chan 100))
(def broadcast-ch (chan))

(defn- filter-out-id [uid uids]
  (remove (fn [id] (= uid id)) uids))

(def m-filter-out-id (memoize filter-out-id))
(def cmd-msg-ch (chan))
(def sync-game-world-ch (chan))

;; broadcast sync

(defn no-sender [s-id]
  (m-filter-out-id s-id (:ws @ws/connected-uids)))

(defn broadcast->ids [uids ev data]
  (let [ev-msg (utils/ev-msg ev data)
        ids-data (conj [] (vec uids) ev-msg)]
    (go
      (>! broadcast-ch ids-data))))

(defn broadcast
  ([ev data]
   (broadcast->ids (:ws @ws/connected-uids) ev data))
  ([ids ev data]
   (broadcast->ids ids ev data)))

(defn- ev-msg-sink []
  (go-loop []
    (let [[uids payload] (<! broadcast-ch)]
      (utils/send-ev-msg uids payload))
    (recur)))

;; game-lobby sync

(defn count-down [ev data]
  (doseq [count data]
    (Thread/sleep 1000)
    (broadcast ev {:count count})))

;; gaming world pillars sync

(defn pillars-generator [ev flappy-start-y pillar-gap]
  (future
    (while (:in-battle? @players)
      (do
        (broadcast ev {:new-pillar (+ 150 (rand-int (- flappy-start-y pillar-gap)))})
        (Thread/sleep 1000)))))

(defn go-game-world []
  (go-loop []
    (let [ev (<! sync-game-world-ch)
          flappy-start-y 300
          pillar-gap 170]
      (when ev
        (pillars-generator :gaming/new-pillar flappy-start-y pillar-gap)))
    (recur)))

;; gaming cmd-msg sync

(defn pack-cmd-msg []
  (loop [accu []]
    (let [cmd (poll! cmd-msg-buffer)]
      (if cmd
        (recur (conj accu cmd))
        accu))))

(defn go-cmd-msg []
  (go-loop []
    (let [cmd-msgs (pack-cmd-msg)]
      (when-not (empty? cmd-msgs)
        (>! cmd-msg-ch cmd-msgs))
      (<! (timeout 50)))
    (recur)))

(defn cmd-msg-sink [ev]
  (go-loop []
    (let [cmd-msgs (<! cmd-msg-ch)
          wrap-cmd-msgs (assoc {} :cmd-msg cmd-msgs)]
      (broadcast ev wrap-cmd-msgs))
    (recur)))

;; init fn

(defn init-sync []
  (ev-msg-sink)
  (go-game-world)
  (go-cmd-msg)
  (cmd-msg-sink :gaming/cmd-msg))
