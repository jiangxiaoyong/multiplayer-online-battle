(ns multiplayer-online-battle.websocket
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            ))

;;------------Set up Sente Websockt-------------------
(defn start-websocket []
  (log/info "Starting websockt...")
  (let [{:keys [ch-recv send-fn connected-uids
                ajax-post-fn ajax-get-or-ws-handshake-fn]} (sente/make-channel-socket! sente-web-server-adapter)]
    (def ch-recv ch-recv)
    (def send-fn send-fn)
    (def connected-uids connected-uids)
    (def ajax-post-fn ajax-post-fn)
    (def ajax-get-or-ws-handshake-fn ajax-get-or-ws-handshake-fn)))
