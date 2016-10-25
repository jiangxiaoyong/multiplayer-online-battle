(ns multiplayer-online-battle.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [ajax.core :refer [GET POST]]
            [cljs.core.async :refer [<! >! put! take! chan close! timeout]]
            [multiplayer-online-battle.landing :refer [landing]]
            [multiplayer-online-battle.utils :refer [ajax-call mount-dom]]
            [multiplayer-online-battle.comm :refer [ws-chan]]
            [multiplayer-online-battle.states :refer [components-state]]))

(enable-console-print!)

(defn fig-reload []
  (.log js/console "figwheel reloaded! ")
  (mount-dom #'landing))

(defn ^:export run []
  (mount-dom #'landing))
