(ns multiplayer-online-battle.game-control
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan sliding-buffer put! close! timeout pub sub]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [world start-game?]]
            [multiplayer-online-battle.flappy-bird :refer [animation-loop flappy-bird-ui]]
            [multiplayer-online-battle.utils :refer [mount-dom ev-msg]]
            [multiplayer-online-battle.network :refer [init-network network-ch-in]]
            [multiplayer-online-battle.comm :refer [chsk-ready?]]
            [multiplayer-online-battle.reactive :refer [reactive-publication]]))

(def jump-step 7)
(def subscribe->reactive (chan))

(defn sub-reactive []
  (sub reactive-publication :push->game-ctrl subscribe->reactive))

(defn jump [{:keys [jump-count] :as state} cur-time]
  (infof "jump!")
  (-> state
      (assoc
          :jump-count (inc jump-count)
          :jump-start-time cur-time
          :jump-step jump-step)))

(defn handle-cmd-msg-stream [cmd-msg-stream]
  (doseq [msg cmd-msg-stream]
    (let [key-type (:key-type msg)
          key-code (:key-code msg)
          player-id (:player-id msg)
          cur-time (:cur-time @world)]
      (swap! world update-in [:all-players player-id] jump cur-time))))

(defn init-subscribe->reactive []
  (sub-reactive)
  (go-loop []
    (let [content (:content (<! subscribe->reactive))
          ev (:ev content)
          data (:data content)]
      (cond
       (= ev :return-to-lobby) (go
                                 (>! network-ch-in data))
       (= ev :upload-cmd-msg) (go
                                (>! network-ch-in data))
       (= ev :cmd-msg-stream) (handle-cmd-msg-stream data)
       (= ev :upload-player-state) (go
                                     (>! network-ch-in data))))
    (recur)))

(defn load-game-state []
  (go
    (let [ready (<! chsk-ready?)]
      (when ready
        (>! network-ch-in (ev-msg :gaming/gaming-state? {}))))))

(defn reset-state [time-stamp]
  (-> @world
      (update-in [:pillar-list] (fn [pls] (map #(assoc % :start-time time-stamp) pls)))
      (update-in [:all-players] (fn [pls] (into {} (map #(assoc-in % [1 :start-time] time-stamp) pls))))
      (update-in [:all-players] (fn [pls] (into {} (map #(assoc-in % [1 :jump-start-time] time-stamp) pls))))
      (assoc-in [:winner] nil)))

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

(defn init-game []
  (init-network)
  (init-subscribe->reactive)
  (load-game-state)
  (fire-game))

(defn fig-reload []
  (.log js/console "figwheel reloaded! ")
  (mount-dom #'flappy-bird-ui))

(defn ^:export run []
  (init-game)
  (mount-dom #'flappy-bird-ui))
