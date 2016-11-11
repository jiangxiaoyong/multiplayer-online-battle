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

(def game-lobby-state (r/atom {}))

(defn me? [{:keys [:user-name] :as msg}]
  (if (= user-name (:user-name (first (vals (:player-current @game-lobby-state)))))
    true
    false))

(defn handle-ev-msg [ev-msg]
  (let [ev-type (first ev-msg)
        payload (:payload (second ev-msg))
        who (first (keys payload))]
    (cond
     (= :game-lobby/players-all ev-type) (swap! game-lobby-state assoc :players-all payload)
     (= :game-lobby/player-come ev-type) (if-not (me? (first (vals payload))) (swap! game-lobby-state update-in [:players-all] conj payload))
     (= :game-lobby/player-current ev-type) (swap! game-lobby-state assoc :player-current payload)
     (= :game-lobby/player-update ev-type) (swap! game-lobby-state update-in [:players-all who :ready?] not))))

(defn player-info []
  (fn [{:keys [user-name ready?]}]
    [:tr {:class (get-in @components-state [:game-lobby :player-come-animate])}
     [:td.user-info
      [:img {:src "http://bootdey.com/img/Content/avatar/avatar1.png"}]
      [:div.user-name user-name]]
     [:td.text-center
      [:h4 {:class (if ready? (get-in @components-state [:game-lobby :player-ready-animate]))}
       [:span {:class (if ready? (get-in @components-state [:game-lobby :player-ready-label]) (get-in @components-state [:game-lobby :player-unready-label]))} (if ready? "ready" "unready")]]]]))

(defn statusBtn []
  (fn [game-lobby-out]
    (let [ready? (:ready? (:player-current @game-lobby-state))]
      (debugf "current state %s" ready?)
      [:button {:class (if ready? "btn btn-lg btn-info btn-block" "btn btn-success btn-lg btn-block")
                :on-click #(do 
                             (swap! game-lobby-state update-in [:player-current :ready?] not)
                             (go (>! game-lobby-out [:game-lobby/player-ready {:payload (:player-current @game-lobby-state)}])))}
       [:sapn {:class (if ready? "glyphicon glyphicon-refresh spinning")}] (if ready? "Waiting" "Ready")])))

(defn players-table []
  (fn []
    [:table.table.user-list
     [:thead
      [:tr
       [:th
        [:span "User"]]
       [:th.text-center
        [:span "Status"]]]]
     [:tbody      
      (for [player (vals (:players-all @game-lobby-state))]
        ^{:key (:client-id player)} [player-info player])]]))

(defn main [game-lobby-in game-lobby-out]
  (fn []
    [:div.game-loby-container.aa
     [:div.row
      [:div.col-lg-8
       [:div.main-box.clearfix
        [:div.table-responsive
         [:div.game-loby-title "Game lobby"]
         [players-table]
         [:div
          [:center
;;           (let [ready?]) (:ready? (:player-current @game-lobby-state))
           [statusBtn game-lobby-out]]]]]]]]))

(defn game-lobby []
  (let [{:keys [game-lobby-in game-lobby-out]} (game-lobby-ch)]
    (r/create-class
     {:componnet-will-mount (fn [_]
                              (log "game lobby will mount"))
      :component-did-mount (fn [_]
                             (go
                               (>! game-lobby-out [:game-lobby/register {:payload "I am new player"}])
                               (>! game-lobby-out [:game-lobby/lobby-state? {:payload "I want all players status"}])) 
                             (go-loop []
                               (let [ev-msg (<! game-lobby-in)]
                                 (debugf "game looby receiving: %s" ev-msg)
                                 (handle-ev-msg ev-msg))
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
