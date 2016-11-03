(ns multiplayer-online-battle.game-lobby
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [clojure.string :as str]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.comm :refer [reconnect start-comm game-lobby-ch]]
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

(defn main [game-lobby-in game-lobby-out]
  (fn []
    [:div.game-loby-container.aa
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
  (let [{:keys [game-lobby-in game-lobby-out]} (game-lobby-ch)]
    (r/create-class
     {:componnet-will-mount (fn [_] 
                              (go
                                (>! game-lobby-out [:game-lobby/register {:data "I am new player"}])
                                (>! game-lobby-out [:game-lobby/all-players-status {:data "I want all players status"}])))
      :component-did-mount (fn [_] 
                              (go-loop []
                                (let [data (<! game-lobby-in)]
                                  (debugf "in game lobby receiving msg %s" data))
                                (recur))(log "game lobby did mount"))
      :component-will-unmount (fn [_] (log "game loby will unmount"))
      :reagent-render (fn []
                        [main game-lobby-in game-lobby-out])})))

(defn fig-reload []
  (.log js/console "figwheel reloaded! ")
  (mount-dom #'game-lobby))

(defn ^:export run []
  (mount-dom #'game-lobby)
  (start-comm))
