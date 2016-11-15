(ns multiplayer-online-battle.flappy-bird
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [clojure.string :as str]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.comm :refer [reconnect start-comm gaming-ch]]
            [multiplayer-online-battle.states :refer [components-state]]
            [multiplayer-online-battle.utils :refer [mount-dom]]))

(defn main []
  (fn []
    [:div "hello world"]))

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
