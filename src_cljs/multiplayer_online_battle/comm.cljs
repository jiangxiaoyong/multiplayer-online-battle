(ns multiplayer-online-battle.comm
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! put! take! chan close! alts! timeout]]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [reagent.debug :refer [dbg log prn]]))

(enable-console-print!)

(defn init-ws []
  (let [{:keys [chsk ch-recv send-fn state] :as ch-map}
        (sente/make-channel-socket! "/chsk" {:type :auto})]
    (defonce ws-recv ch-recv)
    (defonce ws-send send-fn)))

(defn ws-chan []
  (init-ws)
  (let [ch-in (chan)
        ch-out (chan)]
    (go-loop []
      (let [[msg ch] (alts! [ws-recv ch-out])]
        (cond
         (= ch ws-recv) (do
                         (log "receiving msg: " msg)
                         (>! ch-in msg))
         (= ch ch-out) (do
                         (log "sending msg: " msg)
                         (ws-send msg))))
      (recur))
    {:ch-in ch-in
     :ch-out ch-out}))
