(ns multiplayer-online-battle.game-control
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan sliding-buffer put! close! timeout]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [world start-game?]]
            [multiplayer-online-battle.flappy-bird :refer [animation-loop flappy-bird-ui]]
            [multiplayer-online-battle.utils :refer [mount-dom ev-msg]]
            [multiplayer-online-battle.network :refer [init-network]]
            [multiplayer-online-battle.reactive :refer [init-reactive reactive-ch]]
            [multiplayer-online-battle.comm :refer [chsk-ready?]]))

(def ctrl-ch (chan))

(defn init-game-ctrl-ch []
  (go-loop []
    (let [cmd-msgs (<! ctrl-ch)]
      (doseq [msg cmd-msgs]
        (print "msg" msg)))
    (recur)))

(defn load-game-state []
  (go
    (let [ready (<! chsk-ready?)]
      (when ready
        (>! reactive-ch :gaming/gaming-state?)))))

(defn reset-state [time-stamp]
  (-> @world
      (update-in [:pillar-list] (fn [pls] (map #(assoc % :start-time time-stamp) pls)))
      (update-in [:all-players] (fn [pls] (into {} (map #(assoc-in % [1 :start-time] time-stamp) pls))))
      (update-in [:all-players] (fn [pls] (into {} (map #(assoc-in % [1 :jump-start-time] time-stamp) pls))))))

(defn start-game []
  (.requestAnimationFrame
   js/window
   (fn [time-stamp]
     (infof "Start Game" )
     (reset! world (reset-state time-stamp))
     (animation-loop time-stamp))))

(defn fire-game[]
  (go
    (let [fire (<! start-game?)]
      (when fire
        (start-game)))))

;; (defn jump [{:keys [cur-time jump-count] :as state}]
;;   (infof "jump!")
;;   (-> state
;;       (assoc
;;           :jump-count (inc jump-count)
;;           :jump-start-time cur-time
;;           :jump-step jump-step)))

(defn init-game []
  (init-network)
  (init-reactive)
  (init-game-ctrl-ch)
  (load-game-state)
  (fire-game))

(defn fig-reload []
  (.log js/console "figwheel reloaded! ")
  (mount-dom #'flappy-bird-ui))

(defn ^:export run []
  (init-game)
  (mount-dom #'flappy-bird-ui))
