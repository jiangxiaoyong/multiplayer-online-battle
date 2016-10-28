(ns multiplayer-online-battle.comm
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! put! take! chan close! alts! timeout]]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [reagent.debug :refer [dbg log prn]]))

(enable-console-print!)

;;;;;;;;;;;;;;; Define Sente channel socket client

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client! "/chsk" ; Note the same path as before
       {:type :auto ; e/o #{:auto :ajax :ws}
       })]
  (def chsk        chsk)
  (def ch-recv    ch-recv) ; ChannelSocket's receive channel
  (def send-fn   send-fn) ; ChannelSocket's send API fn
  (def chsk-state state))

;;;;;;;;;;;;;; Wrap websock with core.async channel

(let [ws-out (chan)]
  (go-loop []
    (let [msg (<! ws-out)] 
      (send-fn msg))
    (recur))
  (def ws-out ws-out))

(let [ws-in (chan)]
  (go-loop []
    (let [msg (<! ch-recv)]
      (>! ws-in msg))
    (recur))
  (def ws-in ws-in))

;;;;;;;;;;;;;;; Define Sente event handlers

(defmulti event-msg-handler :id)

(defmethod event-msg-handler :default
  [{:as ev-msg :keys [event]}]
  (infof  "unhandlered event: %s" event))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] ?data]
    (if (:first-open? new-state-map)
      (infof "Channel socket succuessfully established! %s" new-state-map)
      (infof "Channel socket state change: %s" new-state-map))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?handshake-data] ?data]
    (infof "Handshake: %s " ?data)))

;;;;;;;;;;;;;;; Set up Sente event router

(defonce ev-router (atom nil))
(defn stop-ev-router [] (when-let [stop-f @ev-router] (stop-f)))
(defn start-ev-router []
  (stop-ev-router)
  (log "Starting client ws event router !")
  (reset! ev-router
    (sente/start-client-chsk-router!
     ch-recv event-msg-handler)))

;;;;;;;;;;;;;;;; Sente utils

(defn reconnect []
  (log "Reconnecting")
  (sente/chsk-reconnect! chsk))

(defn disconnect []
  (log "Disconnecting")
  (sente/chsk-disconnect! chsk))

(defn start-comm []
  (start-ev-router))
