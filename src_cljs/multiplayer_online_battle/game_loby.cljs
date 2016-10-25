(ns multiplayer-online-battle.game-loby
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [cljs.core.async :refer [<! >! put! take! chan close! timeout]]
            [reagent.debug :refer [dbg log prn]]))

(enable-console-print!)

;; (defn game-loby [ch-in ch-out component-attr]
;;   (fn []
;;     [:div {:style {:display (get-in @component-attr [:game-loby :visibility])}} "hello world"]))

(defn game-loby []
  (r/create-class
   {:componnet-will-mount (fn [_] (log "game loby will mount"))
    :component-did-mount (fn [_] (log "game loby did mount"))
    :component-will-unmount (fn [_] (log "game loby will unmount"))
    :reagent-render (fn []
                      [:div "hello world"])}))
