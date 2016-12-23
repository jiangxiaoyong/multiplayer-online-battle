(ns multiplayer-online-battle.reactive
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [world start-game?]]
            [multiplayer-online-battle.comm :refer [cmd-msg-ch]]
            [multiplayer-online-battle.utils :refer [ev-msg]]
            [multiplayer-online-battle.network :refer [network-ch]]))

(enable-console-print!)

(def reactive-ch (chan))
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
      (>! network-ch (ev-msg :gaming/command data)))))

(defn create-cmd-msg-stream []
  (-> js/Rx.Observable
      (.create (fn [observer]
                 (go-loop []
                   (let [cmd-msg (<! cmd-msg-ch)]
                     (.onNext observer cmd-msg))
                   (recur))))))

(defn create-game-ev-stream []
  (-> js/Rx.Observable
      (.create (fn [observer]
                 (go-loop []
                   (let [cmd-msg (<! reactive-ch)]
                     (.onNext observer cmd-msg))
                   (recur))))))

(defn load-game-state-ev []
  (-> (create-game-ev-stream)
      (.filter (fn [ev]
                 (if (= ev :gaming/gaming-state?) true false)))))

(defn return-to-lobby-ev []
  (-> (create-game-ev-stream)
      (.filter (fn [ev]
                 (if (= ev :gaming/return-to-lobby) true false)))))

(defn init-reactive [])

(defn push-ev-msg->network [ev data]
  (go
    (>! network-ch (ev-msg ev data))))

(.subscribe (key-space-up-only)
            (fn [a] (upload-action a))
            (fn [e] (print "key pressing up event error" e))
            (fn [c] (print "key pressing up event complete" c)))

(.subscribe (create-cmd-msg-stream)
            (fn [a] (print "cms-msg stream" a))
            (fn [e] (print "cmd-msg error" e))
            (fn [c] (print "cms-msg complete" c)))

(.subscribe (load-game-state-ev)
            (fn [ev] (push-ev-msg->network ev {}))
            (fn [e] (print "load game state error" e))
            (fn [c] (print "load game state complete" c)))

(.subscribe (return-to-lobby-ev)
            (fn [ev] (push-ev-msg->network ev {}))
            (fn [e] (print "ctrl error" e))
            (fn [c] (print "ctrl complete" c)))

