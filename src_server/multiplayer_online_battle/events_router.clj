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
            [multiplayer-online-battle.websocket :as ws]
            ))


;; Game Lobby events handler

(defn check-game-lobby-state [old new]
  (cond
   (> (count new) (count old)) {:ev-type :game-lobby/player-come
                                :player (apply hash-map (first (filter identity (second (diff old new)))))}
   (< (count new) (count old)) {:ev-type :game-looby/player-leave
                                :player (apply hash-map (first (filter identity (first (diff old new)))))}
   (= (count new) (count old)) {:ev-type :game-lobby/player-update
                                :player (apply hash-map (first (filter identity (second (diff old new)))))}))

(defn fire-game-lobby-sync 
  [key watched old new]
  (when-not (= old new)
    (log/info "fire lobby sync!!!")
    (log/info "old === " old)
    (log/info "new === " new)
    (let [{:keys [ev-type player]} (check-game-lobby-state old new)]
      (synchronize-game-lobby ev-type player))))

;; (defn have-player? [user-name]
;;   (some true?
;;    (for [player @players]
;;       (if (= user-name (get-in player [:user-name]))
;;         true
;;         false))))

(defn register-player [{:as ev-msg :keys [id uid client-id ?data]}]
  (log/info "id:" id)
  (log/info "uid:" uid)
  (log/info "client-id:" client-id)
  (log/info "data" ?data)
  (if (contains? @players (keyword uid))
    (log/info "player already exist!")
    (swap! players conj {(keyword uid) {:client-id client-id :user-name uid :status "unready"}})))

(defn return-player-info [{:as ev-msg :keys [uid client-id]}]
  (ws/send-fn uid [:game-lobby/player-current {:payload {(keyword uid) {:client-id client-id :user-name uid :status "unready"}}}]))

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
  (return-player-info ev-msg)
  (register-player ev-msg)
  (add-watch players :lobby-state fire-game-lobby-sync))

(defmethod event :game-lobby/lobby-state?
  [{:as ev-msg :keys [uid]}]
  (ws/send-fn uid [:game-lobby/players-all {:payload @players}]))

(defmethod event :game-lobby/player-ready
  [{:as ev-msg :keys [client-id uid ?data]}]
  (log/info "player %s is ready now!" uid)
  (log/info "ready plaers payload %s" ev-msg)
  (let [index (.indexOf @players (:payload ?data))]
    (swap! players assoc-in [index :status] "ready")))

;;------------Set up Sente events router-------------
(defonce event-router (atom nil))

(defn stop-events-router []
  (log/info "Stopping socket event router...")
  (when-let [stop-fn @event-router] (stop-fn)))

(defn start-events-router []
  (log/info "Starting socket event router...")
  (stop-events-router)
  (reset! event-router (sente/start-chsk-router! ws/ch-recv event)))
