(ns multiplayer-online-battle.utils
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [ajax.core :refer [GET POST]]
            [reagent.core :as r :refer [atom]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [components-state game-lobby-state world]]))

(enable-console-print!)

(defn mount-dom [dom]
  (if-let [app-dom (.getElementById js/document "app")]
    (r/render-component
     [dom]
     app-dom)))

(defn ajax-call [fn url params] 
  (fn url {:params params}))

(defn num->keyword [uid]
  (keyword (str uid)))

(defn player-exist? [id]
  (if (contains? (:players-all @game-lobby-state) id)
    true
    false))

(defn- wrap-data [key data]
  (assoc {} key data))

(defn ev-msg [ev data]
  (->> data
       (wrap-data :payload)
       (vector ev)))
