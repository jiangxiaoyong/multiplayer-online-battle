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

(def start-y 561)

(defn reset-state [time-state])

(defn sine-wave [st]
  (assoc st
    :flappy-y
    (+ start-y (* 30 (.sin js/Math (/ (:time-delta st) 300))))))

(defn update-flappy [{:keys [time-delta flappy-y] :as state}])

(defn animation-update [time-stamp state]
  (-> state
      (assoc
          :cur-time time-stamp
          :time-delta (- time-stamp (:flappy-start-time state)))
      update-flappy))

(defn animation-loop [time-stamp]
  (let [new-state (swap! flap-state (partial animation-update time-stamp))]
    (when (:timer-running new-state)
      (.requestAnimationFrame js/window animation-loop))))

(defn start-game []
  (.requestAnimationFrame
   js/window
   (fn [time-stamp]
     (reset! flap-state (reset-state time-stamp))
     (animation-loop time-stamp))))

(defn main []
  (fn []
    [:div#board-area
     [:div.board {:onMouseDown (fn [e] (infof "Game area mouse Click!!"))}
      [:h1.score]
      [:a.start-button {:on-click #(start-game)} "START"]
      [:div.flappy {:style {:top "100px"}}]]]))

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
