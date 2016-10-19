(ns multiplayer-online-battle.main
  (:gen-class)
  (:require [org.httpkit.server :refer [run-server]]
            [ring.middleware.reload :as reload]
            (compojure [core :refer [defroutes context routes GET POST ANY]]
                       [route :as route]
                       [handler :refer [site]])))

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
  (GET "/" [] "handling-page!!!")
  (route/not-found "<p>Page not found.</p>")) ;; all other, return 404

(defn -main [& args] ;; entry point, lein run will pick up and start from here
  (let [handler (if (in-dev? args)
                  (reload/wrap-reload (site #'all-routes)) ;; only reload when dev
                  (site all-routes))]
    (run-server handler {:port 8080})))

