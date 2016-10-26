(ns multiplayer-online-battle.game-loby
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [clojure.string :as str]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [multiplayer-online-battle.utils :refer [mount-dom ws-in ws-out]]
            [multiplayer-online-battle.states :refer [components-state]]))

(enable-console-print!)

;; (defn main [ch-in ch-out]
;;   (fn []
;;     [:div {:style {:display (get-in @component-attr [:game-loby :visibility])}} "hello world"]))

(defn game-loby []
  (r/create-class
   {:componnet-will-mount (fn [_] (log "game loby will mount"))
    :component-did-mount (fn [_] (log "game loby did mount"))
    :component-will-unmount (fn [_] (log "game loby will unmount"))
    :reagent-render (fn []
                      [:div "hello world"])}))
