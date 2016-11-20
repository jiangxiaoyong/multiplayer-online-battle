(ns multiplayer-online-battle.flappy-bird
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! chan sliding-buffer put! close! timeout]]
            [clojure.string :as str]
            [cljsjs.react]
            [cljsjs.react.dom]
            [sablono.core :as sab :include-macros true]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.comm :refer [reconnect start-comm gaming-ch]]
            [multiplayer-online-battle.states :refer [components-state flap-starting-state flap-state]]
            [multiplayer-online-battle.utils :refer [mount-dom]]))

(def start-y 300)
(def gravity 0.05)
(def flappy-height 41)
(def bottom-y 561)
(def jump-vel 21)

(defn px [n]
  (str n "px"))

(defn reset-state [time-stamp]
  (-> flap-starting-state
      (assoc
          :start-time time-stamp
          :flappy-start-time time-stamp
          :timer-running true)))

(defn jump [{:keys [cur-time] :as state}]
  (-> state
      (assoc
          :flappy-start-time cur-time
          :initial-vel jump-vel)))

(defn sine-wave [state]
  (assoc state
    :flappy-y
    (+ start-y (* 30 (.sin js/Math (/ (:time-delta state) 300))))))

(defn update-flappy [{:keys [time-delta flappy-y initial-vel] :as state}]
  (let [cur-vel (- initial-vel (* time-delta gravity))
        new-y (- flappy-y cur-vel)
        new-y   (if (> new-y (- bottom-y flappy-height))
                  (- bottom-y flappy-height)
                  new-y)]
    (debugf "time-delt %s cur-vel %s new-y %s" time-delta cur-vel new-y)
    (assoc state :flappy-y new-y))
  )

(defn animation-update [time-stamp state]
  (-> state
      (assoc
          :cur-time time-stamp
          :time-delta (- time-stamp (:flappy-start-time state)))
      update-flappy))

(defn animation-loop [time-stamp]
  (let [new-state (swap! flap-state (partial animation-update time-stamp))]
    (when (:timer-running new-state)
      (debugf "new flap-state %s" @flap-state)
      (.requestAnimationFrame js/window animation-loop))))

(defn start-game []
  (.requestAnimationFrame
   js/window
   (fn [time-stamp]
     (infof "Start Game")
     (reset! flap-state (reset-state time-stamp))
     (animation-loop time-stamp))))

(defn main []
  (fn []
    (let [{:keys [flappy-y]} @flap-state]
      [:div#board-area
       [:div.board {:onMouseDown (fn [e]
                                   (swap! flap-state jump)
                                   (.preventDefault e))}
        [:h1.score]
        [:a.start-button {:on-click #(start-game)} "START"]
        [:div.flappy {:style {:top (px flappy-y)}}]]])))

(defn flappy-bird []
  (let [{:keys [gaming-in gaming-out]} (gaming-ch)]
    (r/create-class
     {:componnet-will-mount (fn [_]
                              (log "flappy bird will mount"))
      :component-did-mount (fn [_]
                             (go-loop []
                               (let [ev-msg (<! gaming-in)]
                                 (debugf "gaming receiving %s" ev-msg))
                               (recur))
                             (log "flappy bird did mount"))
      :component-will-unmount (fn [_] (log "flappy bird will unmount"))
      :reagent-render (fn []
                        [main])})))

(defn fig-reload []
  (.log js/console "figwheel reloaded! ")
  (mount-dom #'flappy-bird))

(defn ^:export run []
  (mount-dom #'flappy-bird)
  (start-comm))
