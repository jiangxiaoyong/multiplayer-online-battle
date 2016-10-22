(ns multiplayer-online-battle.main
  (:gen-class)
  (:require [org.httpkit.server :as server]
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults :as defaults]
            (compojure [core :refer [defroutes context routes GET POST ANY]]
                       [route :as route]
                       [handler :refer [site]])
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            ))

(declare channel-socket)

(defn landing-pg-handler [_]
  (str "<p>hello world</p>"))

;;---------- Define routing ----------------
(defroutes all-routes
  (GET  "/"     req (landing-pg-handler req))
  (GET  "/chsk" req ((:ajax-get-or-ws-handshake-fn channel-socket) req))
  (POST "/chsk" req ((:ajax-post-fn channel-socket) req))
  (GET  "/status" req (str "Connected id: " (pr-str @ (:connected-uids channel-socket))))
  (route/resources "/")
  (route/not-found "<p> Page not found. </p>"))

;;--------- Ring handler ------------------
(def ring-handler
  (-> #'all-routes
      (reload/wrap-reload)
      (defaults/wrap-defaults (assoc-in defaults/site-defaults [:security :anti-forgery] false))))

;;----------- Set up web server----------------
(defonce web-server (atom nil))

(defn stop-web-server []
  (when-let [ws @web-server] 
    (log/info "Stopping server ...")
    ((:stop-fn ws))))

(defn start-web-server
  "Start web server, stop it if it was already running
   and store server info into 'web-server' atom"
  [& [port]]
  (log/info "Starting http-kit server ...")
  (stop-web-server)
  (let [http-kit-stop-fn (server/run-server ring-handler {:port 8080})
        stop-fn (fn [] (http-kit-stop-fn :timeout 100))]
    (reset! web-server {:stop-fn stop-fn})))

;;----------- Sente events handler-------------
(defmulti event 
  "Dispaching event message basd on event-id"
  :id)

(defn event! [{:as ev-msg :keys [id ?data event]}]
  (prn "Evnet-message : " ev-msg)
  (debugf "Sente debug event-message : " ev-msg)
  (event ev-msg))

(defmethod event :default
  [{:as ev-msg :keys [event ?reply-fn]}]
  (prn "Unhandeled event: " event)
  (when ?reply-fn
    (?reply-fn {::umatched-event-as-echoed-from-from-server event})))

;;------------Set up Sente events router-------------
(defonce event-router (atom nil))

(defn stop-router []
  (log/info "Stopping socket event router...")
  (when-let [stop-fn @event-router] (stop-fn)))

(defn start-ws-ev-router []
  (log/info "Starting socket event router...")
  (stop-router)
  (reset! event-router (sente/start-chsk-router! (:ch-recv channel-socket) event!)))

;;------------Set up Websockt-------------------
(defn start-websocket []
  (log/info "Starting websockt...")
  (def channel-socket
    (sente/make-channel-socket-server! sente-web-server-adapter)))

(defn start!
  []
  (start-websocket)
  (start-ws-ev-router)
  (start-web-server)
  )

(defn -main [& args] (start!))
