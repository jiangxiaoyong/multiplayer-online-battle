(ns multiplayer-online-battle.events-router
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.data :refer [diff]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.game-state :refer [players]]
            [multiplayer-online-battle.synchronization :refer [synchronize-game-lobby]]
            [multiplayer-online-battle.websocket :as ws]))

;;;;;;;;;;;;;;;;;;;;;;;; Game Lobby events handler

(defn init-game-lobby-state []
  (swap! players assoc-in [:all-players-ready] false))

(defn check-game-lobby-state [old new]
  (let [new-players-count (count (keys (:all-players new)))
        old-players-count (count (keys (:all-players old)))]
    (cond
     (> new-players-count old-players-count) {:ev-type :game-lobby/player-come
                                              :data (:all-players (second (diff old new)))}
     (< new-players-count old-players-count) {:ev-type :game-looby/player-leave
                                              :data (:all-players (first (diff old new)))}
     (and (= new-players-count old-players-count)
          (contains? (second (diff old new)) :all-players-ready)) {:ev-type :game-lobby/all-players-ready
                                                                   :data {:all-players-ready (:all-players-ready @players)}}
     (and (= new-players-count old-players-count)
          (contains? (second (diff old new)) :all-players)) {:ev-type :game-lobby/player-update
                                                             :data (:all-players (second (diff old new)))})))

(defn fire-game-lobby-sync 
  [key watched old new]
  (when-not (= old new)
    (log/info "old lobby state === " old)
    (log/info "new lobby state === " new)
    (let [{:keys [ev-type data]} (check-game-lobby-state old new)]
      (synchronize-game-lobby ev-type data))))

(defn pre-enter-game []
  (log/info "notify all players pre-entering game")
  (swap! players update-in [:all-players-ready] not)
  (synchronize-game-lobby :game-lobby/pre-enter-game))

(defn check-all-ready []
  (let [all-players-ready? (fn []
                             (every? #(if (:ready? %) true false) (vals (:all-players @players))))]
    (if (all-players-ready?) (pre-enter-game))))

;;;;;;;;;;;;;;;;;;;;;;;;; landing page event handler

(defn register-player [{:as ev-msg :keys [id uid client-id ?data]}]
  (log/info "id:" id)
  (log/info "uid:" uid)
  (log/info "client-id:" client-id)
  (log/info "data" ?data)
  (if (contains? @players (keyword uid))
    (log/info "player %s already exist!" uid)
    (swap! players update-in [:all-players] (fn [existing new] (into {} (conj existing new))) {(keyword uid) {:client-id client-id :user-name uid :avatar-img (str (rand-int 8) ".png") :ready? false}})))

(defn return-player-info [{:as ev-msg :keys [uid client-id]}]
  (ws/send-fn uid [:game-lobby/player-current {:payload ((keyword uid) (:all-players @players))}]))

;;----------- Sente events handler-------------
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

(defmethod event :game-lobby/register
  [{:as ev-msg}]
  (log/info "register player")
  (register-player ev-msg)  
  (return-player-info ev-msg)
  (add-watch players :lobby-state fire-game-lobby-sync))

(defmethod event :game-lobby/lobby-state?
  [{:as ev-msg :keys [uid]}]
  (ws/send-fn uid [:game-lobby/players-all {:payload (:all-players @players)}]))

(defmethod event :game-lobby/player-ready
  [{:as ev-msg :keys [client-id uid ?data]}]
  (log/info "player %s is ready now!" uid)
  (swap! players update-in [:all-players (keyword uid) :ready?] not)
  (check-all-ready))

;;------------Set up Sente events router-------------
(defonce event-router (atom nil))

(defn stop-events-router []
  (log/info "Stopping socket event router...")
  (when-let [stop-fn @event-router] (stop-fn)))

(defn start-events-router []
  (log/info "Starting socket event router...")
  (stop-events-router)
  (init-game-lobby-state)
  (reset! event-router (sente/start-chsk-router! ws/ch-recv event)))
