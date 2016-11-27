(ns multiplayer-online-battle.utils
  (:gen-class)
  (:require 
   [clojure.tools.logging :as log]
   [clojure.pprint :refer [pprint]]
   [multiplayer-online-battle.game-state :refer [players]]))

(defn num->keyword [uid]
  (keyword (str uid)))

(defn payload [data]
  {:payload data})

(defn select-player [uid]
  (->  @players
       (:all-players)
       ((num->keyword uid))))

(defn- avatar [player img]
  (assoc player :avatar-img img))

(defn- ready? [player status]
  (assoc player :ready? status))

(defn- time-stamp [player time]
  (assoc player :time-stamp time))

(defn- user-name [player name]
  (assoc player :user-name name))

(defn- player-init-state [player img status time]
  (fn [name]
    (-> player
        (avatar img)
        (ready? status)
        (time-stamp time)
        (user-name name))))

(defn create-player [name]
  (let [img (str (rand-int 8) ".png")
        ready? false
        time (System/currentTimeMillis)
        player-map {}
        player (player-init-state player-map img ready? time)]
    (player name)))
