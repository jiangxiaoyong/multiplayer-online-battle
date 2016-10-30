(ns multiplayer-online-battle.events-router
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            ))

;;------------Set up Websockt-------------------
(defn start-websocket []
  (log/info "Starting websockt...")
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]} (sente/make-channel-socket! sente-web-server-adapter)]
    (def ch-recv ch-recv)
    (def send-fn send-fn)
    (def connected-uids connected-uids)
    (def ajax-post-fn ajax-post-fn)
    (def ajax-get-or-ws-handshake-fn ajax-get-or-ws-handshake-fn)))


;;----------- Sente events handler-------------
(defmulti event :id)

(defmethod event :default
  [{:as ev-msg :keys [event ?reply-fn]}]
  (log/info "Unhandeled event: " event)
  (when ?reply-fn
    (?reply-fn {:umatched-event-as-echoed-from-from-server event})))

(defmethod event :test/game
  [{:as ev-msg :keys [id ?data event]}]
  (log/info "uid:" (:uid ev-msg))
  (log/info "id: " (:id ev-msg))
  (log/info "clint id !  "  (:client-id ev-msg))
  (log/info "data" ?data)
  (log/info "event" event))

(defmethod event :register-user/username
  [{:as ev-msg :keys [id ?data event]}]
  (log/info "TEST Evnet event: " event))

;;------------Set up Sente events router-------------
(defonce event-router (atom nil))

(defn stop-router []
  (log/info "Stopping socket event router...")
  (when-let [stop-fn @event-router] (stop-fn)))

(defn start-events-router []
  (log/info "Starting socket event router...")
  (stop-router)
  (reset! event-router (sente/start-chsk-router! ch-recv event)))

