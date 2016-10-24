(ns multiplayer-online-battle.game-loby
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [cljs.core.async :refer [<! >! put! take! chan close! timeout]]
            [clojure.pprint :refer [pprint]]))

(enable-console-print!)

(defn game-loby [ch-in ch-out component-attr]
  (fn []
    [:div {:style {:display (get-in @component-attr [:game-loby :visibility])}} "hello world"]))
