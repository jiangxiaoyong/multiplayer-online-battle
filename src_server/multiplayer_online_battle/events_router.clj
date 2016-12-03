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
            [multiplayer-online-battle.synchronization :refer [synchronize-game-lobby]]
            [multiplayer-online-battle.websocket :as ws]
            [multiplayer-online-battle.utils :as utils]))

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

(defn check-game-lobby-state [old new]
  (let [new-players-count (count (keys (:all-players new)))
        old-players-count (count (keys (:all-players old)))
        changed-map (second (diff old new))
        changed-val (first (vals changed-map))]
    (cond
     (> new-players-count old-players-count) (utils/ev-data-map :game-lobby/player-come changed-val (first (keys changed-map)))
     (< new-players-count old-players-count) (utils/ev-data-map :game-lobby-leave (log/info "player leave"))
     (and (= new-players-count old-players-count)
          (contains? changed-map :all-players-ready)) (utils/ev-data-map :game-lobby/all-players-ready {:all-players-ready (:all-players-ready @players)} 123)
     (and (= new-players-count old-players-count)
          (contains? changed-map :all-players)) (utils/ev-data-map :game-lobby/player-update changed-val (first (keys changed-map))))))

(defn fire-game-lobby-sync 
  [key watched old new]
  (when-not (= old new)
    (newline)
    (log/info "old lobby state === " old)
    (log/info "new lobby state === " new)
    (let [{:keys [ev-type data]} (check-game-lobby-state old new)]
      (log/info "ev-type data" ev-type data)
      (synchronize-game-lobby ev-type data))))

(defn pre-enter-game []
  (log/info "notify all players pre-entering game")
  (swap! players update-in [:all-players-ready] not)
  (let [pre-enter-game-count-down "game-lobby/pre-enter-game-count-down"
        pre-enter-game-dest "game-lobby/pre-enter-game-dest"]
    (<!! (synchronize-game-lobby (keyword pre-enter-game-count-down))) ;; ugly solution, use blocking take that waiting timeout chan close,which returen nil
    (synchronize-game-lobby (keyword pre-enter-game-dest) {:dest "/gaming"})))

(defn check-all-players-ready []
  (if-not (nil? (:all-players @players))
    (every? #(if (= (:status %) (:ready utils/player-status)) true false) (vals (:all-players @players)))
    false))

(defn ready-to-gaming? []
  (if (check-all-players-ready) (pre-enter-game)))

(defn return-info [uid payload]
  (utils/send-fn uid payload))

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

(defmethod event :game-lobby/sub-ev
  [{:as ev-msg}]
  (add-watch players :lobby-state fire-game-lobby-sync))

(defmethod event :game-lobby/lobby-state?
  [{:as ev-msg :keys [uid]}]
  (let [ev-player-cur (partial utils/ev-msg :game-lobby/player-current)
        ev-all-players (partial utils/ev-msg :game-lobby/players-all)]
    (return-info uid (ev-player-cur (utils/target-player uid)))
    (return-info uid (ev-all-players (utils/all-players)))))

(defmethod event :game-lobby/player-ready
  [{:as ev-msg :keys [client-id uid ?data]}]
  (log/info "player %s is ready now!" uid)
  (swap! players assoc-in [:all-players (utils/num->keyword uid) :status] (:ready utils/player-status))
  (ready-to-gaming?))

;; gaming events

(defmethod event :gaming/get-player-info
  [{:as ev-msg}])

(defmethod event :gaming/cmd
  [{:as ev-msg}]
  (process-cmd-msg ev-msg))

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
