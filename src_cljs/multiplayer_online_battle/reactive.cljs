(ns multiplayer-online-battle.reactive
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [world start-game?]]
            [multiplayer-online-battle.control :as ctrl]
            [multiplayer-online-battle.comm :refer [gaming-in gaming-out]]
            [multiplayer-online-battle.utils :refer [ev-msg]]))

(enable-console-print!)

(def arrow-keys #{37 38 39 40})
(def space-key #{32})

(defn key-ev [type]
  (fn [element]
    (-> js/Rx.Observable
        (.fromEvent element type))))

(def keyDowns ((key-ev "keydown") js/document))
(def keyUps ((key-ev "keyup") js/document))

(defn keyboard-ev []
  (-> keyDowns
      (.merge keyUps)
      (.distinctUntilChanged nil (fn [a b] (and (if (= (.-keyCode a) (.-keyCode b)) true false)
                                                (if (= (.-type a) (.-type b)) true false))))))

(defn keys-only [keys-target]
  (-> (keyboard-ev)
      (.filter (fn [key] (contains? keys-target (.-keyCode key))))))

(defn key-space-up-only []
  (-> (keys-only space-key)
      (.filter (fn [key] (if (= (.-type key) "keydown") true false)))))

(defn upload-action [action]
  (let [cur-id (first (keys (:player-current @world)))
        data {:player-id cur-id
              :key-type (.-type action)
              :key-code (.-keyCode action)}]
    (go
      (>! gaming-out (ev-msg :gaming/action data)))))

(.subscribe (key-space-up-only) 
            (fn [a] (upload-action a))
            (fn [e] (print "error" e))
            (fn [c] (print "complete" c)))

