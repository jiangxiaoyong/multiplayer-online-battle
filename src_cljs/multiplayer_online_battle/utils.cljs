(ns multiplayer-online-battle.utils
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [ajax.core :refer [GET POST]]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [components-state game-lobby-state world flap-starting-state start-game?]]
            [multiplayer-online-battle.comm :refer [cmd-msg-ch]]))

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

(defn set-flappys-position [flappys]
  (let [count (count flappys)
        positions (take count (iterate #(+ 80 %) 212))]))

(defn- construct-all-flappy-state [p-ids p-info]
  (loop [ids p-ids
         info p-info
         states (map #(assoc flap-starting-state :flappy-x %) (->> (iterate #(+ 80 %) 212)
                                                                   (take (count ids))))]
    (when-not (empty? ids)
      (swap! world assoc-in [:all-players (first ids)] (merge (first states) (first info)))
      (recur (rest ids) (rest info) (rest states)))))

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
     (= :game-lobby/player-leave ev-type) (swap! game-lobby-state update-in [:players-all] (fn [pls] (into {} (remove #(= (first %) (first payload-keys)) pls))))
     (= :game-lobby/all-players-ready ev-type) (swap! game-lobby-state update-in [:all-players-ready] not)
     (= :game-lobby/pre-enter-game-count-down ev-type) (swap! components-state assoc-in [:game-lobby :style :btn-ready-label] (:count payload))
     (= :game-lobby/pre-enter-game-dest ev-type) (.assign js/window.location (:dest payload))
     (= :gaming/redirect ev-type) (.assign js/window.location (:dest payload))
     (= :gaming/player-current ev-type) (swap! world assoc :player-current payload)
     (= :gaming/players-all ev-type) (do
                                       (construct-all-flappy-state payload-keys payload-val)
                                       (swap! world update-in [:timer-running] not)
                                       (swap! world update-in [:players-loaded?] not)
                                       (go
                                         (>! start-game? true)))
     (= :gaming/cmd-msg ev-type) (go
                                   (>! cmd-msg-ch (first payload-val)))
     (= :gaming/player-die ev-type) (swap! world update-in [:all-players] (fn [pls] (into {} (remove #(= (first %) (:player-id payload)) pls))))
     (= :gaming/you-are-winner ev-type) (swap! world assoc-in [:winner] (:player-id payload))
     )))


