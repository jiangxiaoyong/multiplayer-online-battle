(ns multiplayer-online-battle.synchronization
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.core.async :as async  :refer (<! <!! >! >!! put! take! poll! pipe chan go go-loop timeout close!)]
            [multiplayer-online-battle.websocket :as ws]
            [taoensso.timbre    :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.game-state :refer [players]]
            [multiplayer-online-battle.utils :as utils]))

;;;;;;;;;;;; Frame Synchronization for game lobby

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

(defn- send-to-id [id ev data]
  (let [uids (conj #{} id)
        ev-msg (utils/ev-msg ev data)
        ids-data (conj [] (vec uids) ev-msg)
        ch-out (chan)]
    (go
      (>! ch-out ids-data))
    ch-out))

(defn- sync-fn
  ([f & args] 
   (let [ch (apply f args)]
     (go-loop []
       (let [data (<! ch)]
         (>! sync-ch data))
       (recur)))))

(defn count-down [ev data]
  (doseq [count data]
    (Thread/sleep 1000)
    (sync-fn broadcast-all ev {:count count})))

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
  (def send-only (partial sync-fn send-to-id))
  (ev-msg-sink sync-ch))

(def cmd-msg-buffer (chan 100))

(defn pack-cmd-msgs []
  (loop [accu []]
    (let [cmd (poll! cmd-msg-buffer)]
      (if cmd
        (recur (conj accu cmd))
        accu))))

;; (defn cmd-msg-sink [ev]
;;   (go-loop []
;;     (let [cmd-msgs (pack-cmd-msgs)
;;           wrap-cmd-msgs (assoc {} :cmd-msg cmd-msgs)]
;;       (log/info "cmd-msgs" cmd-msgs)
;;       (emit ev wrap-cmd-msgs)
;;       (<! (timeout 3000))
;;       )
;;     (recur)))

(defn cmd-msg-sink [ev]
  (future
    (loop [cmd-msgs (pack-cmd-msgs)
           wrap-cmd-msgs (assoc {} :cmd-msg cmd-msgs)]
      (log/info "cmd-msgs" cmd-msgs)
      (emit ev wrap-cmd-msgs)
      (Thread/sleep 3000)
      (recur (pack-cmd-msgs) (assoc {} :cmd-msg cmd-msgs))
      )))

;;(def cmd-sink (cmd-msg-sink :gaming/cmd-msg))



