(ns multiplayer-online-battle.events-router
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async  :refer (<! <!! >! >!! put! chan go go-loop timeout)]
            [clojure.pprint :refer [pprint]]
            [clojure.data :refer [diff]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.game-state :refer [players reset-game]]
            [multiplayer-online-battle.synchronization :refer [broadcast no-sender count-down cmd-msg-buffer sync-game-world-ch] :as sync]
            [multiplayer-online-battle.websocket :as ws]
            [multiplayer-online-battle.utils :as utils :refer [num->keyword keyword->num]]))

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

(defn handle-player-leave [uid]
  (if (utils/one-player-left?)
    (reset-game)
    (when (utils/player-exist? (num->keyword uid))
      (let [ids-no-sender (no-sender uid)]
        (swap! players update-in [:all-players] (fn [pls] (into {} (remove #(= (first %) (num->keyword uid)) pls))))
        (broadcast ids-no-sender :game-lobby/player-leave (utils/target-player uid))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Gaming events handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn process-command [{:as ev-msg :keys [?data uid]}]
  (let [payload (:payload ?data)]
    (log/info "upload cmd-msg")
    (>!! cmd-msg-buffer payload)))

(defn find-winner [players]
  (let [all-players (:all-players players)
        pls-info (vals all-players)
        num-players (count (keys all-players))
        num-alive (fn [coll]
                    (->> coll
                         (map #(:alive? %))
                         (filter #(if % true false))
                         (count)))
        get-winner-id (fn [coll]
                        (->> coll
                             (filter #(if (= true (:alive? (second %))) true false))
                             (into {})
                             (keys)
                             (first)))]
    (if (= 1 num-players) (first (keys all-players))
        (if (= 1 (num-alive pls-info)) (keyword->num (get-winner-id all-players)) nil))))

(defn handle-player-die [{:as ev-msg :keys [?data uid]}]
  (let [ids-no-sender (no-sender uid)]
    (swap! players assoc-in [:all-players (num->keyword uid) :alive?] false)
    (broadcast ids-no-sender :gaming/player-die {:player-id (num->keyword uid)})
    (when-let [id (find-winner @players)]
      (broadcast [id] :gaming/you-are-winner {:player-id (num->keyword id)})
      (swap! players assoc-in [:in-battle?] false))))

(defn check-all-game-loaded [players]
  (if-not (empty? (:all-players players))
    (every? #(if (= (:game-loaded? %) true) true false) (vals (:all-players players))) ;;TODO need to conbimed with check-all-players-status
    false))

(defn handle-game-loaded [{:as ev-msg :keys [?data uid]}]
  (swap! players assoc-in [:all-players (num->keyword uid) :game-loaded?] true)
  (when (check-all-game-loaded @players)
    (swap! players assoc-in [:in-battle?] true)
    (>!! sync-game-world-ch true)
    (broadcast :gaming/game-loaded {:all-game-loaded true})))

(defn handle-return-to-lobby [{:as ev-msg :keys [uid]}]
  (swap! players assoc-in [:all-players (num->keyword uid) :status] (:unready utils/player-status)) ;;change cur-player status to unready on server
  (swap! players assoc-in [:all-players-ready] false)
  (swap! players assoc-in [:all-players (num->keyword uid) :alive?] true)
  (swap! players assoc-in [:all-players (num->keyword uid) :game-loaded?] false)
  (broadcast :game-lobby/player-update (utils/target-player uid))
  (broadcast [uid] :gaming/redirect {:dest "/gamelobby"}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Game Lobby events handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pre-enter-game []
  (log/info "notify all players pre-entering game")
  (swap! players update-in [:all-players-ready] not)
  (broadcast :game-lobby/all-players-ready {:all-ready true})
  (count-down :game-lobby/pre-enter-game-count-down [3 2 1 0])
  (broadcast :game-lobby/pre-enter-game-dest {:dest "/gaming"})
  (swap! players update-in [:all-players] (fn [pls] (into {} (map #(assoc-in % [1 :status] (:gaming utils/player-status))) pls))))

(defn check-all-players-status [status]
  (if-not (empty? (:all-players @players))
    (every? #(if (= (:status %) (status utils/player-status)) true false) (vals (:all-players @players)))
    false))

(defn ready-to-gaming? []
  (when (check-all-players-status :ready) (pre-enter-game)))

(defn handle-player-ready [{:as ev-msg :keys [client-id uid ?data]}]
  (log/info "player %s is ready now!" uid)
  (swap! players assoc-in [:all-players (utils/num->keyword uid) :status] (:ready utils/player-status))
  (broadcast :game-lobby/player-update (utils/target-player uid))
  (ready-to-gaming?))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente events handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti event :id)

(defmethod event :default
  [{:as ev-msg :keys [event ?reply-fn]}]
  (log/info "Unhandeled event: " event)
  (when ?reply-fn
    (?reply-fn {:umatched-event-as-echoed-from-from-server event})))

(defmethod event :chsk/uidport-close
  [{:as ev-msg :keys [uid]}]
  (log/info "Players leave!!!" uid)
  (handle-player-leave uid))

(defmethod event :test/game
  [{:as ev-msg :keys [id ?data event]}]
  (log/info "uid:" (:uid ev-msg))
  (log/info "id: " (:id ev-msg))
  (log/info "clint id !  "  (:client-id ev-msg))
  (log/info "data" ?data)
  (log/info "event" event))

;; game-looby events

(defmethod event :game-lobby/states
  [{:as ev-msg :keys [uid]}]
  (return-players-state uid "game-lobby"))

(defmethod event :game-lobby/player-ready
  [{:as ev-msg}]
  (handle-player-ready ev-msg))

;; gaming events

(defmethod event :gaming/states
  [{:as ev-msg :keys [uid]}]
  (return-players-state uid "gaming"))

(defmethod event :gaming/command
  [{:as ev-msg}]
  (process-command ev-msg))

(defmethod event :gaming/iam-dead
  [{:as ev-msg}]
  (handle-player-die ev-msg))

(defmethod event :gaming/game-loaded
  [{:as ev-msg}]
  (handle-game-loaded ev-msg))

(defmethod event :gaming/return-to-lobby
  [{:as ev-msg :keys [uid]}]
  (handle-return-to-lobby ev-msg)) 

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
