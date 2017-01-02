(ns multiplayer-online-battle.reactive
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close! pub sub]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [world start-game?]]
            [multiplayer-online-battle.utils :refer [ev-msg]]))

(enable-console-print!)

(def reactive-ch-in (chan))
(def reactive-publisher (chan))
(def reactive-publication (pub reactive-publisher #(:topic %)))

(def arrow-keys #{37 38 39 40})
(def space-key #{32})

;;; keyboard event stream ;;;

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

;;; command messages stream ;;;

(defn publish [topic content]
  (go
    (>! reactive-publisher {:topic topic :content content})))

(defn upload-cmd-msg [action]
  (let [cur-id (first (keys (:player-current @world)))
        data {:player-id cur-id
              :key-type (.-type action)
              :key-code (.-keyCode action)}]
    (publish :gaming-ev {:ev :upload-cmd-msg :payload data})))

;;; game events stream ;;;

(defn create-game-ev-stream []
  (-> js/Rx.Observable
      (.create (fn [observer]
                 (go-loop []
                   (let [cmd-msg (<! reactive-ch-in)]
                     (.onNext observer cmd-msg))
                   (recur))))))

(def game-ev-stream (-> (create-game-ev-stream)
                        (.publish)
                        (.refCount)))

(defn game-lobby-ev []
  (-> game-ev-stream
      (.filter (fn [ev] (if (= :game-lobby (:where ev)) true false)))))

(defn gaming-ev []
  (-> game-ev-stream
      (.filter (fn [ev] (if (= :gaming (:where ev)) true false)))))

;;; subscribe events ;;;

(.subscribe (key-space-up-only)
            (fn [a] (upload-cmd-msg a))
            (fn [e] (print "key pressing up event error" e))
            (fn [c] (print "key pressing up event complete" c)))

(.subscribe (game-lobby-ev)
            (fn [ev] (publish :game-lobby-ev {:ev (:ev ev) :payload (:payload ev)}))
            (fn [e] (print "game-lobby-ev error" e))
            (fn [c] (print "game-lobby-ev complete" c)))

(.subscribe (gaming-ev)
            (fn [ev] (publish :gaming-ev {:ev (:ev ev) :payload (:payload ev)}))
            (fn [e] (print "gaming-ev error" e))
            (fn [c] (print "gaming-ev complete" c)))

