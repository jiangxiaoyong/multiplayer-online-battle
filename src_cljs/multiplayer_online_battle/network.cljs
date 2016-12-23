(ns multiplayer-online-battle.network
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! put! take! chan close! alts! timeout]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [world]]
            [multiplayer-online-battle.comm :refer [start-comm gaming-ch]]
            [multiplayer-online-battle.utils :refer [handle-ev-msg]]))

(def network-ch (chan))

(defn load-gaming-ch []
  (let [{:keys [gaming-in gaming-out]} (gaming-ch)]
    (def gaming-in gaming-in)
    (def gaming-out gaming-out)))

(defn init-network-ch []
  (go-loop []
    (let [[ev-msg ch] (alts! [network-ch gaming-in])]
      (cond
       (= ch network-ch) (>! gaming-out ev-msg)
       (= ch gaming-in) (do
                           (debugf "gaming receiving %s" ev-msg)
                           (handle-ev-msg ev-msg)))) ;;TODO, refactor handle incoming msg
    (recur)))

(defn init-network []
  (start-comm)
  (load-gaming-ch)
  (init-network-ch))


