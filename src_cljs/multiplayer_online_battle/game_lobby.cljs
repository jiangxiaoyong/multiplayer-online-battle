(ns multiplayer-online-battle.game-lobby
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [clojure.string :as str]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [multiplayer-online-battle.comm :refer [ws-in ws-out]]
            [multiplayer-online-battle.states :refer [components-state]]
            [multiplayer-online-battle.utils :refer [mount-dom]]))

(enable-console-print!)

(defn player-info2 []
  (fn []
    [:tr
     [:td.user-info
      [:img {:src "http://bootdey.com/img/Content/avatar/avatar2.png"}]
      [:div.user-name "ddd bbb"]]
     [:td.text-center
      [:h4
       [:span.label.label-success "ready"]]]]))

(defn player-info1 []
  (fn []
    [:tr
     [:td.user-info
      [:img {:src "http://bootdey.com/img/Content/avatar/avatar1.png"}]
      [:div.user-name "ABC CDE"]]
     [:td.text-center
      [:h4
       [:span.label.label-default "Unready"]]]]))

(defn statusBtn []
  [:button.btn.btn-success.btn-lg.btn-block "Ready"])

(defn player-table []
  (fn []
    [:table.table.user-list
     [:thead
      [:tr
       [:th
        [:span "User"]]
       [:th.text-center
        [:span "Status"]]]]
     [:tbody
      [player-info1]
      [player-info2]]]))

(defn main [ch-in ch-out]
  (fn []
    [:div.game-loby-container
     [:div.row
      [:div.col-lg-8
       [:div.main-box.clearfix
        [:div.table-responsive
         [:div.game-loby-title "Game lobby"]
         [player-table]
         [:div
          [:center
           [statusBtn]]]]]]]]))

(defn game-lobby []
  (r/create-class
   {:componnet-will-mount (fn [_] (log "game loby will mount"))
    :component-did-mount (fn [_] (log "game loby did mount"))
    :component-will-unmount (fn [_] (log "game loby will unmount"))
    :reagent-render (fn []
                      [main ws-in ws-out])}))

(defn fig-reload []
  (.log js/console "figwheel reloaded! ")
  (mount-dom #'game-lobby))

(defn ^:export run []
  (mount-dom #'game-lobby))
