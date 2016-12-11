(ns multiplayer-online-battle.utils
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [ajax.core :refer [GET POST]]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [components-state game-lobby-state world flap-starting-state]]))

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

(defn set-flappys-position [flappys]
  (let [count (count flappys)
        positions (take count (iterate #(+ 80 %) 212))]))

(defn handle-ev-msg [ev-msg]
  (let [ev-type (first ev-msg)
        payload (:payload (second ev-msg))
        payload-val (vals payload)
        payload-keys (keys payload)
        who (first (keys payload))]
    (cond
     (= :game-lobby/players-all ev-type) (swap! game-lobby-state assoc :players-all payload)
     (= :game-lobby/player-come ev-type) (if-not (player-exist? who) (swap! game-lobby-state update-in [:players-all] conj payload))
     (= :game-lobby/player-current ev-type) (swap! game-lobby-state assoc :player-current (first payload-val))
     (= :game-lobby/player-update ev-type) (swap! game-lobby-state assoc-in [:players-all who :status] (:status (first payload-val)))
     (= :game-lobby/all-players-ready ev-type) (swap! game-lobby-state update-in [:all-players-ready] not)
     (= :game-lobby/pre-enter-game-count-down ev-type) (swap! components-state assoc-in [:game-lobby :style :btn-ready-label] (:count payload))
     (= :game-lobby/pre-enter-game-dest ev-type) (.assign js/window.location (:dest payload))
     (= :gaming/player-current ev-type) (swap! world assoc :player-current payload)
     (= :gaming/players-all ev-type) (do
                                       (loop [ids payload-keys
                                                info payload-val
                                                states (map #(assoc flap-starting-state :flappy-x %) (take (count payload-keys) (iterate #(+ 80 %) 212)))]
                                           (if-not (empty? ids)
                                             (do
                                               (swap! world assoc-in [:all-players (first ids)] (merge (first states) (first info)))
                                               (recur (rest ids) (rest info) (rest states)))))
                                       (swap! world update-in [:timer-running] not)
                                       (swap! world update-in [:start?] not))
     )))


