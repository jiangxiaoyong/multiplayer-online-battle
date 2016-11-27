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
            [multiplayer-online-battle.websocket :as ws]
            [multiplayer-online-battle.synchronization :refer [synchronize-game-lobby]]
            [multiplayer-online-battle.game-state :refer [players]]
            [multiplayer-online-battle.events-router :refer [check-all-players-ready]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]))

;;---------- Routing handlers--------------

(defn home-pg-handler [_]
  (response/content-type 
   (response/resource-response "public/index.html")
   "text/html"))

(defn login-player [uid user-name]
  (log/info "uid = " uid "user-name" user-name)
  (if (contains? (:all-players @players) (keyword uid))
    (log/info "player %s already exist!" uid)
    (swap! players update-in [:all-players] (fn [existing new] (into {} (conj existing new))) {(keyword (str uid)) {:user-name user-name :avatar-img (str (rand-int 8) ".png") :ready? false}})))

(defn login-handler [req]
  (if-not (check-all-players-ready)
    (let [{:keys [session params]} req
          {:keys [user-name]} params
          uid (System/currentTimeMillis)]
      (log/info "login user = " user-name)
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

;;---------- Define routing ----------------
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
