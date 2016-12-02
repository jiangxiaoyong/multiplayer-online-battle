(ns multiplayer-online-battle.utils
  (:gen-class)
  (:require 
   [clojure.tools.logging :as log]
   [clojure.pprint :refer [pprint]]
   [clojure.data :refer [diff]]
   [multiplayer-online-battle.game-state :refer [players]]
   [multiplayer-online-battle.websocket :as ws]))

(def player-status
  {:ready 0
   :unready 1
   :gaming 2})

(defn num->keyword [uid]
  (keyword (str uid)))

(defn- wrap-payload [data]
  {:payload data})

(defn- wrap-player-id [val uid]
  (fn [map]
    (assoc map (num->keyword uid) val)))

(defn select-player [players uid]
  (-> players
      (:all-players)
      ((num->keyword uid))))

(defn target-player [uid]
  (let [player (-> @players
                   (select-player uid)
                   (wrap-player-id uid))]
    (player {})))
 
(defn- avatar [player img]
  (assoc player :avatar-img img))

(defn- status? [player status]
  (assoc player :status status))

(defn- time-stamp [player time]
  (assoc player :time-stamp time))

(defn- user-name [player name]
  (assoc player :user-name name))

(defn- player-init-state [player img status time]
  (fn [name]
    (-> player
        (avatar img)
        (status? status)
        (time-stamp time)
        (user-name name))))

(defn create-player [name]
  (let [img (str (rand-int 8) ".png")
        status (:unready player-status)
        time (System/currentTimeMillis)
        player-map {}
        player (player-init-state player-map img status time)]
    (player name)))

(defn all-players []
  (-> @players
      (:all-players)))

(defn ev-msg [ev-type data]
  (->> data
       (wrap-payload)
       (vector ev-type)))

(defn send-fn [uid ev]
  (ws/send-fn uid ev))

(defn ev-data-map [ev-type data]
  {:ev-type ev-type
   :data data})
