(ns multiplayer-online-battle.flappy-bird
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! chan sliding-buffer put! close! timeout]]
            [clojure.string :as str]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.comm :refer [reconnect start-comm gaming-ch]]
            [multiplayer-online-battle.states :refer [components-state flap-starting-state flap-state]]
            [multiplayer-online-battle.utils :refer [mount-dom]]))

(def flappy-start-y 300)
(def gravity 0.02)
(def flappy-x 212)
(def flappy-width 57)
(def flappy-height 41)
(def ground-y 561)
(def jump-step 7)
(def ground-move-speed -0.15)
(def pillar-spacing 324)
(def pillar-gap 180)
(def pillar-width 86)

(defn floor [x] (.floor js/Math x))

(defn translate [start-pos ground-move-speed time]
  (floor (+ start-pos (* ground-move-speed time))))

(defn px [n]
  (str n "px"))

;;;;;;;;;;;;;;;;;;;;;;;;;
;; Score
;;;;;;;;;;;;;;;;;;;;;;;;

(defn score [{:keys [cur-time start-time] :as st}]
  (let [score (- (.abs js/Math (floor (/ (- (* (- cur-time start-time) ground-move-speed) 544)
                               pillar-spacing)))
                 4)]
  (assoc st :score (if (neg? score) 0 score))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; pillars animation
;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; pillars collision

(defn in-pillar? [{:keys [cur-x]}]
  (and (>= (+ flappy-x flappy-width)
           cur-x)
       (< flappy-x (+ cur-x pillar-width))))

(defn in-pillar-gap? [{:keys [flappy-y]} {:keys [gap-top]}]
  (and (< gap-top flappy-y)
       (> (+ gap-top pillar-gap)
          (+ flappy-y flappy-height))))

(defn bottom-collision? [{:keys [flappy-y]}]
  (>= flappy-y (- ground-y flappy-height)))

(defn collision? [{:keys [pillar-list] :as st}]
  (if (some #(or (and (in-pillar? %)
                      (not (in-pillar-gap? st %)))
                 (bottom-collision? st)) pillar-list)
    (assoc st :timer-running false)
    st))

;; animation

(defn pillars-pair-height [{:keys [gap-top] :as p}]
  (assoc p
    :upper-height gap-top
    :lower-height (- ground-y gap-top pillar-gap)))

(defn update-pillars-pair-height [state]
  (update-in state [:pillar-list]
             (fn [pillar-list]
               (map pillars-pair-height pillar-list))))

(defn curr-pillar-pos [cur-time {:keys [start-pos-x start-time] }]
  (translate start-pos-x ground-move-speed (- cur-time start-time)))

(defn new-pillar [cur-time start-pos-x]
  {:start-time cur-time
   :start-pos-x start-pos-x
   :cur-x      start-pos-x
   :gap-top    (+ 100 (rand-int (- flappy-start-y pillar-gap)))})

(defn update-pillars-list [{:keys [pillar-list cur-time] :as st}]
  (let [pillars-with-pos (map #(assoc % :cur-x (curr-pillar-pos cur-time %)) pillar-list)
        pillars-in-world (sort-by
                          :cur-x
                          (filter #(> (:cur-x %) (- pillar-width)) pillars-with-pos))]
    (infof "pillars in world %s" pillars-in-world)
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
      update-pillars-list
      update-pillars-pair-height
      collision?
      ground
      score))

(defn animation-loop [time-stamp]
  (let [new-state (swap! flap-state (partial animation-update time-stamp))]
    (when (:timer-running new-state)
      (debugf "new flap-state %s" @flap-state)
      (.requestAnimationFrame js/window animation-loop))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Gaming control
;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn pillar [{:keys [cur-x upper-height lower-height]}]
  [:div.pillars
   [:div.pillar.pillar-upper {:style {:left (px cur-x)
                                       :height upper-height}}]
   [:div.pillar.pillar-lower {:style {:left (px cur-x)
                                       :height lower-height}}]])

(defn main []
  (fn []
    (let [{:keys [flappy-y timer-running ground-pos pillar-list score]} @flap-state]
      [:div#board-area
       [:div.board {:onMouseDown (fn [e]
                                   (swap! flap-state jump)
                                   (.preventDefault e))}
        [:h1.score score]
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
