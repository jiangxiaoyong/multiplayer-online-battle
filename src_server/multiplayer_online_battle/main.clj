(ns multiplayer-online-battle.main
  (:gen-class)
  (:require [org.httpkit.server :as server]
            [ring.middleware.reload :as reload]
            (compojure [core :refer [defroutes context routes GET POST ANY]]
                       [route :as route]
                       [handler :refer [site]])
            [clojure.tools.logging :as log]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]))

(defn app [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "hello HTTP!"})

(defn bar [] 
  (+ 1 2))

(defn in-dev? [_] true)

(defonce server (atom nil))

(defn stop-server []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server :timeout 100)
    (reset! server nil)))

(defroutes all-routes
  (GET "/" [] "handling-page")
  (route/not-found "<p>Page not found.</p>")) ;; all other, return 404

(defn -main [& args] ;; entry point, lein run will pick up and start from here
  (let [handler (if (in-dev? args)
                  (reload/wrap-reload (site #'all-routes)) ;; only reload when dev
                  (site all-routes))]
    (reset! server (server/run-server handler {:port 8080}))))

(declare channel-socket)

(defn start-websocket []
  (defonce channel-socket
    (sente/make-channel-socket-server! sente-web-server-adapter)))

(defn start!
  []
  (log/info "Starting serever...")
  ;;(start-websocket)
  ;;(start-router)
  ;;(start-web-server)
  )


(defn -main [& args] (start!))
