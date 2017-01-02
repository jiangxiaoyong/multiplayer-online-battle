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

(def cmd-msg-ch (chan)) ;; TODO, need to remove

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

(defn create-cmd-msg-stream []
  (-> js/Rx.Observable
      (.create (fn [observer]
                 (go-loop []
                   (let [cmd-msg (<! cmd-msg-ch)]
                     (.onNext observer cmd-msg))
                   (recur))))))

(defn publish [topic content]
  (go
    (>! reactive-publisher {:topic topic :content content})))

(defn upload-cmd-msg [action]
  (let [cur-id (first (keys (:player-current @world)))
        data {:player-id cur-id
              :key-type (.-type action)
              :key-code (.-keyCode action)}]
    (publish :push->game-ctrl {:ev :upload-cmd-msg :data (ev-msg :gaming/command data)})))

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

(defn common-ev []
  (-> game-ev-stream
      (.filter (fn [ev] (if (= :common-ev (:where ev)) true false)))))

(defn game-lobby-ev []
  (-> game-ev-stream
      (.filter (fn [ev] (if (= :game-lobby (:where ev)) true false)))))

(defn gaming-ev []
  (-> game-ev-stream
      (.filter (fn [ev] (if (= :gaming (:where ev)) true false)))))

;;; TODO ;;;

(defn return-to-lobby-ev []
  (-> game-ev-stream
      (.filter (fn [ev]
                 (if (= ev :gaming/return-to-lobby) true false)))))

(defn player-die-ev []
  (-> game-ev-stream
      (.filter (fn [ev]
                 (if (= ev :gaming/player-die) true false)))))

(defn upload-player-state [state]
  (let [cur-id (first (keys (:player-current @world)))
        data {:player-id cur-id}]
    (publish :push->game-ctrl {:ev :upload-player-state :data (ev-msg :gaming/player-die data)})))

(defn init-reactive [])

;;; subscribe events ;;;

(.subscribe (key-space-up-only)
            (fn [a] (upload-cmd-msg a))
            (fn [e] (print "key pressing up event error" e))
            (fn [c] (print "key pressing up event complete" c)))

(.subscribe (create-cmd-msg-stream)
            (fn [cmd-msg] (do
                      (print "cms-msg stream" cmd-msg)
                      (publish :push->game-ctrl {:ev :cmd-msg-stream :data cmd-msg})))
            (fn [e] (print "cmd-msg error" e))
            (fn [c] (print "cms-msg complete" c)))

(.subscribe (return-to-lobby-ev)
            (fn [ev] (publish :push->game-ctrl {:ev :return-to-lobby :data (ev-msg ev {})}))
            (fn [e] (print "ctrl error" e))
            (fn [c] (print "ctrl complete" c)))

(.subscribe (player-die-ev)
            (fn [ev] (upload-player-state ev))
            (fn [e] (print "ctrl error" e))
            (fn [c] (print "ctrl complete" c)))

(.subscribe (common-ev)
            (fn [ev] (publish :common-ev {:ev (:ev ev) :payload (:payload ev)}))
            (fn [e] (print "common-ev error" e))
            (fn [c] (print "common-ev complete" c)))

(.subscribe (game-lobby-ev)
            (fn [ev] (publish :game-lobby-ev {:ev (:ev ev) :payload (:payload ev)}))
            (fn [e] (print "game-lobby-ev error" e))
            (fn [c] (print "game-lobby-ev complete" c)))

(.subscribe (gaming-ev)
            (fn [ev] (publish :gaming-ev {:ev (:ev ev) :payload (:payload ev)}))
            (fn [e] (print "gaming-ev error" e))
            (fn [c] (print "gaming-ev complete" c)))

