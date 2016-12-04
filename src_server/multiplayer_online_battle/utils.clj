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

(defn- wrap-data [key data]
  (assoc {} key data))

(defn select-player [uid players]
  (->> players
       (:all-players)
       ((num->keyword uid))))

(defn target-player [uid]
   (->> @players
        (select-player uid)
        (wrap-data (num->keyword uid))))

(defn- add-key-val [k v m]
  (assoc m k v))

(defn- player-init-state [player img status time]
  (fn [name]
    (->> player
         (add-key-val :avatar-img img)
         (add-key-val :status status)
         (add-key-val :time-stamp time)
         (add-key-val :user-name  name))))

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

(defn ev-msg [ev data]
  (->> data
       (wrap-data :payload)
       (vector ev)))

(defn send-fn [uid ev]
  (ws/send-fn uid ev))

(defn send-ev-msg [uids payload]
  (doseq [uid uids]
    (ws/send-fn uid payload)))

(defn ev-data-map [ev-type data id]
  (->> {}
       (add-key-val :ev-type ev-type)
       (add-key-val :data data)
       (add-key-val :id id)))
