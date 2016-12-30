(ns multiplayer-online-battle.flappy-bird
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan sliding-buffer put! close! timeout]]
            [clojure.string :as str]
            [goog.events.KeyCodes :as KeyCodes]
            [goog.events :as events]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [components-state flap-starting-state world-staring-state world start-game? pillar-buf]]
            [multiplayer-online-battle.reactive :refer [reactive-ch-in]]
            [multiplayer-online-battle.utils :refer [ev-msg]]
            ))

(enable-console-print!)

(def flappy-start-y 300)
(def gravity 0.02)
(def flappy-width 57)
(def flappy-height 41)
(def ground-y 561)

(def ground-move-speed -0.20)
(def pillar-spacing 310)
(def pillar-gap 170)
(def pillar-width 86)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; common
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn floor [x] (.floor js/Math x))

(defn translate [start-pos ground-move-speed time]
  (floor (+ start-pos (* ground-move-speed time))))

(defn px [n]
  (str n "px"))

(defn get-new-pillar []
  (go
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;
;; Score
;;;;;;;;;;;;;;;;;;;;;;;;

(defn score [{:keys [cur-time start-time] :as st}]
  (let [score (- (.abs js/Math (floor (/ (- (* (- cur-time start-time) ground-move-speed) 2200)
                               pillar-spacing)))
                 4)]
  (assoc st :score (if (neg? score) 0 score))))

;;;;;;;;;;;;;;;;;;;;;;;;
;; winner
;;;;;;;;;;;;;;;;;;;;;;;

(defn check-winner [cur-id {:keys [winner] :as st}]
  (if-let [winner-id winner]
    (when (= cur-id winner)
      (assoc st :timer-running false))
    st))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; pillars animation
;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; pillars collision

(defn in-pillar? [{:keys [flappy-x]} {:keys [cur-x]}]
  (and (>= (+ flappy-x flappy-width)
           cur-x)
       (< flappy-x (+ cur-x pillar-width))))

(defn in-pillar-gap? [{:keys [flappy-y]} {:keys [gap-top]}]
  (and (< gap-top flappy-y)
       (> (+ gap-top pillar-gap)
          (+ flappy-y flappy-height))))

(defn bottom-collision? [{:keys [flappy-y]}]
  (>= flappy-y (- ground-y flappy-height)))

(defn collision? [cur-id st]
  (let [{:keys [pillar-list all-players]} st
        flappy-player (cur-id all-players)]
    (if (some #(or (and ((partial in-pillar? flappy-player) %)
                        (not ((partial in-pillar-gap? flappy-player) %)))
                   (bottom-collision? flappy-player)) pillar-list)
      (do 
        (go
          (>! reactive-ch-in :gaming/player-die))
        (assoc st :timer-running false))
      st)))

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

(defn new-pillar [cur-time start-pos-x new-pillar-height]
  {:start-time cur-time
   :start-pos-x start-pos-x
   :cur-x      start-pos-x
   :gap-top    new-pillar-height})

(defn update-pillars-list [{:keys [pillar-list cur-time new-pillar-height] :as st}]
  (let [pillars-with-pos (map #(assoc % :cur-x (curr-pillar-pos cur-time %)) pillar-list)
        pillars-in-world (sort-by
                          :cur-x
                          (filter #(> (:cur-x %) (- pillar-width)) pillars-with-pos))]
    ;;(infof "pillars in world %s" pillars-in-world)
    (assoc st
      :pillar-list
      (if (< (count pillars-in-world) 4)
        (conj pillars-in-world
              (new-pillar
               cur-time
               (+ pillar-spacing
                  (:cur-x (last pillars-in-world)))
               new-pillar-height))
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

(defn sine-wave [player]
  (assoc-in player
            [1 :flappy-y]
            (+ flappy-start-y (* 30 (.sin js/Math (/ (:time-delta (second player)) 300))))))

(defn update-flappy [player]
  (let [state (second player)
        {:keys [time-delta flappy-y jump-step jump-count]} state]
    (if (pos? jump-count)
      (let [by-gravity (- jump-step (* time-delta gravity))
            cur-y (- flappy-y by-gravity)
            cur-y   (if (> cur-y (- ground-y flappy-height))
                      (- ground-y flappy-height)
                      cur-y)]
        (assoc-in player [1 :flappy-y] cur-y))
      (sine-wave player)
      )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; animation logic per frame
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn animation-update [time-stamp state]
  (let [cur-id (first (keys (:player-current @world)))
        collision-flappy-cur? (partial collision? cur-id)
        iam-winner? (partial check-winner cur-id)]
    (-> state
        (assoc :cur-time time-stamp)
        (update-in [:all-players] (fn [pls] (into {} (map #(assoc-in % [1 :time-delta] (- time-stamp (:jump-start-time (second %)))) pls))))
        (update-in [:all-players] (fn [pls] (into {} (map update-flappy pls))))
        update-pillars-list
        update-pillars-pair-height
        collision-flappy-cur?
        ground
        iam-winner?
        ;;score
        )))

(defn animation-loop [time-stamp]
  (let [new-state (swap! world (partial animation-update time-stamp))]
    (when (:timer-running new-state)
      (.requestAnimationFrame js/window animation-loop))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; React UI
;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pillar [{:keys [cur-x upper-height lower-height]}]
  ^{:key cur-x}
  [:div.pillars
   [:div.pillar.pillar-upper {:style {:left (px cur-x)
                                      :height upper-height}}]
   [:div.pillar.pillar-lower {:style {:left (px cur-x)
                                      :height lower-height}}]])

(defn flappy []
  (fn [{:keys [flappy-y flappy-x user-name]}]
    [:div.flappy {:style {:top (px flappy-y) :left flappy-x}}
     [:div.flappy-name user-name]]))

(defn main []
  (fn []
    ;;(let [{:keys [flappy-y timer-running score ground-pos pillar-list]} @world])
    (let [{:keys [all-players ground-pos pillar-list timer-running game-loaded? winner]} @world]
      [:div#board-area
       [:div.board
        ;; [:h1.score score]
        (if-not timer-running
          [:a.start-button {:on-click #(go
                                         (>! reactive-ch-in :gaming/return-to-lobby))} (if-not (nil? winner) "WINNER!" "RETURN")]
          [:span])
        [:div (map pillar pillar-list)]
        (when game-loaded?
          (for [player (vals all-players)]
            ^{:key (:time-stamp player)} [flappy player]))
        [:div.scrolling-border {:style {:background-position-x (px ground-pos)}}]]])))

(defn flappy-bird-ui []
  (r/create-class
   {:reagent-render (fn []
                      [main])
    :component-will-mount (fn []
                            (log "gaming will mount"))
    :component-did-mount (fn []
                           (log "gaming did mount"))}))
