(ns multiplayer-online-battle.network
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! put! take! chan close! alts! timeout]]
            [clojure.string :as str]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [game-lobby-state world]]
            [multiplayer-online-battle.reactive :refer [reactive-ch-in]]))

(enable-console-print!)

;;;;;;;;;;;;;;; Define Sente channel socket client

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client! "/chsk" ; Note the same path as before
       {:type :ws ; e/o #{:auto :ajax :ws}
       })]
  (def chsk        chsk)
  (def ch-recv    ch-recv) ; ChannelSocket's receive channel
  (def send-fn   send-fn) ; ChannelSocket's send API fn
  (def chsk-state state))

(defonce ev-router (atom nil))

;;;;;;;;;;;;;;; channels

(def chsk-ready? (chan))
(def network-ch-in (chan))

;;;;;;;;;;;;;;; Define Sente event handlers

(defmulti event-msg-handler :id)

(defmethod event-msg-handler :default
  [{:as ev-msg :keys [event]}]
  (infof  "unhandlered event: %s" event))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] ?data]
    (when (:first-open? new-state-map)
      (infof "Channel socket succuessfully established!")
      (go (>! chsk-ready? true)))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?handshake-data] ?data]
    (infof "Handshake: %s " ?data)))

(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (let [where (comp keyword namespace first)
        ev (comp keyword name first)
        payload (comp :payload second)]
    (go (>! reactive-ch-in {:where (where ?data)
                            :ev (ev ?data)
                            :payload (payload ?data)}))))

;;;;;;;;;;;;;;; start and stop  Sente event router

(defn stop-ev-router [] (when-let [stop-f @ev-router] (stop-f)))

(defn start-ev-router []
  (stop-ev-router)
  (infof "Starting client ws event router !")
  (reset! ev-router
    (sente/start-chsk-router!
     ch-recv event-msg-handler)))

(defn reconnect []
  (infof "Reconnecting")
  (sente/chsk-reconnect! chsk))

(defn disconnect []
  (infof "Disconnecting")
  (sente/chsk-disconnect! chsk))

;;;;;;;;;;;; send msg via Sente send-fn

(defn start-sending-loop []
  (go-loop []
    (let [data (<! network-ch-in)]
      (infof "sending msg %s" data)
      (send-fn data))
    (recur)))

;; init fn

(defn init-network []
  (start-ev-router)
  (start-sending-loop))


