(ns multiplayer-online-battle.events-router
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async  :refer (<! <!! >! >!! put! chan go go-loop timeout)]
            [clojure.pprint :refer [pprint]]
            [clojure.data :refer [diff]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.game-state :refer [players players-init-state]]
            [multiplayer-online-battle.synchronization :refer [broadcast emit init-sync count-down]]
            [multiplayer-online-battle.websocket :as ws]
            [multiplayer-online-battle.utils :as utils :refer [num->keyword]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler common
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn return-players-state [uid where]
  (let [ev-type-player-cur (keyword (str where "/player-current"))
        ev-type-player-all (keyword (str where "/players-all"))
        ev-player-cur (partial utils/ev-msg ev-type-player-cur)
        ev-player-all (partial utils/ev-msg ev-type-player-all)]
    (utils/send-fn uid (ev-player-cur (utils/target-player uid)))
    (utils/send-fn uid (ev-player-all (utils/all-players)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Gaming events handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn process-cmd-msg [{:as ev-msg :keys [?data uid]}]
  (log/info "Receiving msg" ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Game Lobby events handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn reset-game []
  (reset! players players-init-state))

(defn pre-enter-game []
  (log/info "notify all players pre-entering game")
  (swap! players update-in [:all-players-ready] not)
  (emit :game-lobby/all-players-ready {:all-ready true})
  (count-down :game-lobby/pre-enter-game-count-down [3 2 1 0])
  (emit :game-lobby/pre-enter-game-dest {:dest "/gaming"})
  (swap! players update-in [:all-players] (fn [pls] (into {} (map #(assoc-in % [1 :status] (:gaming utils/player-status))) pls))))

(defn check-all-players-ready []
  (if-not (nil? (:all-players @players))
    (every? #(if (= (:status %) (:ready utils/player-status)) true false) (vals (:all-players @players)))
    false))

(defn ready-to-gaming? []
  (when (check-all-players-ready) (pre-enter-game)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente events handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;; game-looby events

(defmethod event :game-lobby/lobby-state?
  [{:as ev-msg :keys [uid]}]
  (return-players-state uid "game-lobby"))

(defmethod event :game-lobby/player-ready
  [{:as ev-msg :keys [client-id uid ?data]}]
  (log/info "player %s is ready now!" uid)
  (swap! players assoc-in [:all-players (utils/num->keyword uid) :status] (:ready utils/player-status))
  (emit :game-lobby/player-update (utils/target-player uid))
  (ready-to-gaming?))

;; gaming events

(defmethod event :gaming/gaming-state?
  [{:as ev-msg :keys [uid]}]
  (return-players-state uid "gaming"))

(defmethod event :gaming/cmd
  [{:as ev-msg}]
  (process-cmd-msg ev-msg))

(defmethod event :gaming/return-to-lobby
  [{:as ev-msg :keys [uid]}]
  (log/info "return-to-lobby" uid)
  (swap! players assoc-in [:all-players (num->keyword uid) :status] (:unready utils/player-status)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Set up Sente events router
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce event-router (atom nil))

(defn stop-events-router []
  (log/info "Stopping socket event router...")
  (when-let [stop-fn @event-router] (stop-fn)))

(defn start-events-router []
  (log/info "Starting socket event router...")
  (stop-events-router)
  (reset! event-router (sente/start-chsk-router! ws/ch-recv event)))
