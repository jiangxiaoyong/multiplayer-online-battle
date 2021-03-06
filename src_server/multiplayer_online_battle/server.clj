(ns multiplayer-online-battle.server
  (:gen-class)
  (:require [org.httpkit.server :as server]
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [multiplayer-online-battle.routing :refer [ring-handler]]))


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
  (stop-web-server)
  (let [port (Integer/parseInt (or (System/getenv "PORT") "8080"))
        http-kit-stop-fn (server/run-server ring-handler {:port port})
        stop-fn (fn [] (http-kit-stop-fn :timeout 100))]
    (log/info "Starting http-kit server at port " port) 
    (reset! web-server {:stop-fn stop-fn})))

