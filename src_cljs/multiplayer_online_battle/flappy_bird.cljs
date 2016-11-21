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
(def jump-step 11)
(def horiz-vel -0.15)

(defn px [n]
  (str n "px"))

(defn reset-state [time-stamp]
  (-> flap-starting-state
      (assoc
          :start-time time-stamp
          :jump-start-time time-stamp
          :timer-running true)))

;; background border animation

(defn floor [x] (.floor js/Math x))

(defn translate [start-pos vel time]
  (floor (+ start-pos (* time vel))))

(defn border [{:keys [cur-time] :as state}]
  (-> state
      (assoc :border-pos (mod (translate 0 horiz-vel cur-time) 23))))

;; flappy bird animation

(defn jump [{:keys [cur-time jump-count] :as state}]
  (infof "jump!")
  (-> state
      (assoc
          :jump-count (inc jump-count)
          :jump-start-time cur-time
          :jump-step jump-step)))

(defn sine-wave [state]
  (assoc state
    :flappy-y
    (+ start-y (* 30 (.sin js/Math (/ (:time-delta state) 300))))))

(defn update-flappy [{:keys [time-delta flappy-y jump-step jump-count] :as state}]
  (if (pos? jump-count)
    (let [by-gravity (- jump-step (* time-delta gravity))
          cur-y (- flappy-y by-gravity)
          cur-y   (if (> cur-y (- bottom-y flappy-height))
                    (- bottom-y flappy-height)
                    cur-y)]
      (assoc state :flappy-y cur-y))
    (sine-wave state)))

(defn animation-update [time-stamp state]
  (-> state
      (assoc
          :cur-time time-stamp
          :time-delta (- time-stamp (:jump-start-time state)))
      update-flappy
      border))

(defn animation-loop [time-stamp]
  (let [new-state (swap! flap-state (partial animation-update time-stamp))]
    (when (:timer-running new-state)
      (debugf "new flap-state %s" @flap-state)
      (.requestAnimationFrame js/window animation-loop))))

(defn start-game []
  (.requestAnimationFrame
   js/window
   (fn [time-stamp]
     (infof "Start Game" )
     (reset! flap-state (reset-state time-stamp))
     (animation-loop time-stamp))))

(defn main []
  (fn []
    (let [{:keys [flappy-y timer-running border-pos]} @flap-state]
      [:div#board-area
       [:div.board {:onMouseDown (fn [e]
                                   (swap! flap-state jump)
                                   (.preventDefault e))}
        [:h1.score]
        (if-not timer-running
          [:a.start-button {:on-click #(start-game)} "START"]
          [:span])
        [:div.flappy {:style {:top (px flappy-y)}}]
        [:div.scrolling-border {:style {:background-position-x (px border-pos)}}]]])))

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
