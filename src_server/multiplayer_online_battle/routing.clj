(ns multiplayer-online-battle.routing
  (:gen-class)
  (:require 
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults :as defaults]
            [ring.util.response :as response]
            (compojure [core :refer [defroutes context routes GET POST ANY]]
                       [route :as route]
                       [handler :refer [site]])
            [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.websocket :as ws]
            [multiplayer-online-battle.synchronization :refer [broadcast emit init-sync]]
            [multiplayer-online-battle.game-state :refer [players]]
            [multiplayer-online-battle.events-router :refer [check-all-players-status]]
            [multiplayer-online-battle.utils :as utils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routing handlers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn home-pg-handler [_]
  (response/content-type 
   (response/resource-response "public/index.html")
   "text/html"))

(defn login-player [uid user-name]
  (log/info "login-player uid = " uid "user-name" user-name)
  (if (contains? (:all-players @players) (utils/num->keyword uid))
    (log/info "player %s already exist!" uid)
    (let [new-player {(utils/num->keyword uid) (utils/create-player user-name)}]
      (swap! players update-in [:all-players] (fn [existing new] (into {} (conj existing new))) new-player)
      (broadcast :game-lobby/player-come new-player))))


(defn login-handler [req]
  (log/info "check-all-status" (check-all-players-ready))
  (if-not (or (check-all-players-status :ready)
              (check-all-players-status :gaming))
    (let [{:keys [session params]} req
          {:keys [user-name]} params
          uid (System/currentTimeMillis)]
      (log/info "login user = " user-name)
      (init-sync uid)
      (login-player uid user-name)
      {:status 200 :session (assoc session :uid uid)})
    {:status 409 :body {:msg "battle in progress"}}))

(defn game-lobby-handler [req]
  (response/content-type
   (response/resource-response "public/gameLobby.html")
   "text/html"))

(defn gaming-handler [req]
  (response/content-type
   (response/resource-response "public/gaming.html")
   "text/html"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Define routing
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defroutes all-routes
  (GET  "/"     req (home-pg-handler req))
  (GET  "/chsk" req (ws/ajax-get-or-ws-handshake-fn req))
  (POST "/chsk" req (ws/ajax-post-fn req))
  (GET  "/status" req (str "Connected id: " (pr-str @ws/connected-uids)))
  (GET "/gamelobby" req (game-lobby-handler req))
  (GET "/gaming" req (gaming-handler req))
  (POST "/login" req (login-handler req))
  (route/resources "/")
  (route/not-found "<p> Page not found. </p>"))

;;--------- Ring handler ------------------
(def ring-handler
  (-> #'all-routes
      (reload/wrap-reload)
      (defaults/wrap-defaults (assoc-in defaults/site-defaults [:security :anti-forgery] false))))
