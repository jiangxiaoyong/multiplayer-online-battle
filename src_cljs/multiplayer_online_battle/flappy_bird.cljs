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

(def flappy-start-y 300)
(def gravity 0.05)
(def flappy-height 41)
(def ground-y 561)
(def jump-step 11)
(def ground-move-speed -0.15)
(def pillar-spacing 324)
(def pillar-gap 158)
(def pillar-width 86)

(defn floor [x] (.floor js/Math x))

(defn translate [start-pos val time]
  (floor (+ start-pos (* time val))))

(defn px [n]
  (str n "px"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; pillars animation
;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pillar-offset [{:keys [gap-top] :as p}]
  (assoc p
    :upper-height gap-top
    :lower-height (- ground-y gap-top pillar-gap)))

(defn pillar-offsets [state]
  (update-in state [:pillar-list]
             (fn [pillar-list]
               (map pillar-offset pillar-list))))

(defn curr-pillar-pos [cur-time {:keys [pos-x start-time] }]
  (translate pos-x ground-move-speed (- cur-time start-time)))

(defn new-pillar [cur-time pos-x]
  {:start-time cur-time
   :pos-x      pos-x
   :cur-x      pos-x
   :gap-top    (+ 60 (rand-int (- flappy-start-y pillar-gap)))})

(defn update-pillars [{:keys [pillar-list cur-time] :as st}]
  (let [pillars-with-pos (map #(assoc % :cur-x (curr-pillar-pos cur-time %)) pillar-list)
        pillars-in-world (sort-by
                          :cur-x
                          (filter #(> (:cur-x %) (- pillar-width)) pillars-with-pos))]
    (assoc st
      :pillar-list
      (if (< (count pillars-in-world) 3)
        (conj pillars-in-world
              (new-pillar
               cur-time
               (+ pillar-spacing
                  (:cur-x (last pillars-in-world)))))
        pillars-in-world))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; background border animation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ground [{:keys [cur-time] :as state}]
  (-> state
      (assoc :ground-pos (mod (translate 0 ground-move-speed cur-time) 23))))

;;;;;;;;;;;;;;;;;;;;;;;;;
;; flappy bird animation
;;;;;;;;;;;;;;;;;;;;;;;;

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
    (+ flappy-start-y (* 30 (.sin js/Math (/ (:time-delta state) 300))))))

(defn update-flappy [{:keys [time-delta flappy-y jump-step jump-count] :as state}]
  (if (pos? jump-count)
    (let [by-gravity (- jump-step (* time-delta gravity))
          cur-y (- flappy-y by-gravity)
          cur-y   (if (> cur-y (- ground-y flappy-height))
                    (- ground-y flappy-height)
                    cur-y)]
      (assoc state :flappy-y cur-y))
    (sine-wave state)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; animation logic per frame
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn animation-update [time-stamp state]
  (-> state
      (assoc
          :cur-time time-stamp
          :time-delta (- time-stamp (:jump-start-time state)))
      update-flappy
      ;;update-pillars
      pillar-offsets
      ground))

(defn animation-loop [time-stamp]
  (let [new-state (swap! flap-state (partial animation-update time-stamp))]
    (when (:timer-running new-state)
      (debugf "new flap-state %s" @flap-state)
      (.requestAnimationFrame js/window animation-loop))))

(defn reset-state [time-stamp]
  (-> flap-starting-state
      (update-in [:pillar-list] (fn [pls] (map #(assoc % :start-time time-stamp) pls)))
      (assoc
          :start-time time-stamp
          :jump-start-time time-stamp
          :timer-running true)))

(defn start-game []
  (.requestAnimationFrame
   js/window
   (fn [time-stamp]
     (infof "Start Game" )
     (reset! flap-state (reset-state time-stamp))
     (animation-loop time-stamp))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; React UI
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pillar [{:keys [cur-x pos-x upper-height lower-height]}]
  [:div.pillars
   [:div.pillar.pillar-upper {:style {:left (px cur-x)
                                       :height upper-height}}]
   [:div.pillar.pillar-lower {:style {:left (px cur-x)
                                       :height lower-height}}]])

(defn main []
  (fn []
    (let [{:keys [flappy-y timer-running ground-pos pillar-list]} @flap-state]
      [:div#board-area
       [:div.board {:onMouseDown (fn [e]
                                   (swap! flap-state jump)
                                   (.preventDefault e))}
        [:h1.score]
        (if-not timer-running
          [:a.start-button {:on-click #(start-game)} "START"]
          [:span])
        [:div (map pillar pillar-list)]
        [:div.flappy {:style {:top (px flappy-y)}}]
        [:div.scrolling-border {:style {:background-position-x (px ground-pos)}}]]])))

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
