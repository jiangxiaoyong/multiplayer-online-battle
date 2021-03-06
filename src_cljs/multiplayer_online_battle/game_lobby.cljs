(ns multiplayer-online-battle.game-lobby
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [clojure.string :as str]
            [ajax.core :refer [GET POST]]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.reactive :refer [reactive-ch-in]]
            [multiplayer-online-battle.states :refer [components-state game-lobby-state game-lobby-init-state]]
            [multiplayer-online-battle.utils :refer [mount-dom]]
            [multiplayer-online-battle.game-control :refer [init-game-lobby]]))

(enable-console-print!)

(defn reset-game-lobby-state []
  (reset! game-lobby-state game-lobby-init-state))

(defn get-state-value [k]
  (get-in @components-state (map #(keyword %) (str/split k #" "))))

(defn player-info []
  (let [style-ready-animated (get-state-value "game-lobby style player-ready-animated")
        style-ready-span (get-state-value "game-lobby style player-ready-span")
        style-ready-label (get-state-value "game-lobby style player-ready-label")
        style-unready-span (get-state-value "game-lobby style player-unready-span")
        style-unready-label (get-state-value "game-lobby style player-unready-label")
        style-gaming-span (get-state-value "game-lobby style player-gaming-span")
        style-gaming-label (get-state-value "game-lobby style player-gaming-label")
        style-player-come-animated (get-state-value "game-lobby style player-come-animated")
        player-status-ready (get-state-value "game-lobby player-status ready")
        player-status-unready (get-state-value "game-lobby player-status unready")
        player-status-gaming (get-state-value "game-lobby player-status gaming")
        avatar-img-path (fn [img]
                          (str "/images/avatars/" img))]
    (fn [{:keys [user-name status avatar-img]}]
      [:tr {:class style-player-come-animated}
       [:td.user-info
        [:img {:src (avatar-img-path avatar-img)}]
        [:div.user-name user-name]]
       [:td.text-center
        [:h4 {:class (if (= status player-status-ready) style-ready-animated)}
         [:span {:class (cond
                         (= status player-status-ready) style-ready-span 
                         (= status player-status-unready) style-unready-span
                         (= status player-status-gaming) style-gaming-span)} 
          (cond
           (= status player-status-ready) style-ready-label
           (= status player-status-unready) style-unready-label
           (= status player-status-gaming) style-gaming-label)]]]])))

(defn statusBtn []
  (let []
    (fn []
      (let [player-status (:status (:player-current @game-lobby-state))
            all-players-ready? (:all-players-ready @game-lobby-state)
            num-players (count (keys (:players-all @game-lobby-state)))
            style-btn-ready-label (get-state-value "game-lobby style btn-ready-label")
            style-btn-ready-animated (get-state-value "game-lobby style btn-ready-animated")
            style-btn-ready (get-state-value "game-lobby style btn-ready")
            style-btn-unready (get-state-value "game-lobby style btn-unready")
            style-btn-unready-label (get-state-value "game-lobby style btn-unready-label")
            style-btn-not-active (get-state-value "game-lobby style btn-not-active")
            player-status-ready (get-state-value "game-lobby player-status ready")
            player-status-unready (get-state-value "game-lobby player-status unready")
            player-status-gaming (get-state-value "game-lobby player-status gaming")]
        [:a {:class (if (< num-players 2) style-btn-not-active
                        (if (= player-status player-status-ready) style-btn-ready style-btn-unready))
             :href "#"
             :role "button"
             :disabled (if (< num-players 2) true false)
             :on-click #(do 
                          (swap! game-lobby-state assoc-in [:player-current :status] player-status-ready)
                          (go (>! reactive-ch-in {:where :game-lobby :ev :player-ready :payload (:player-current @game-lobby-state)})))}
         [:sapn {:class (if (= player-status player-status-ready) style-btn-ready-animated)}]
         [:div.status-btn-label (if (= player-status player-status-ready) style-btn-ready-label style-btn-unready-label)]]))))

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
        ^{:key (:time-stamp player)} [player-info player])]]))

(defn main []
  (fn []
    [:div.game-lobby-container
     [:div.row
      [:div.col-lg-3]
      [:div.col-lg-6
       [:div.main-box.clearfix
        [:div.table-responsive
         [:div.game-loby-title "Game lobby"]
         [players-table]
         [:div
          [:center
           [statusBtn]]]]]]
      [:div.col-lg-3]]]))

(defn game-lobby []
  (r/create-class
   {:reagent-render (fn []
                      [main])
    :component-will-mount (fn [_]
                            (log "game lobby will mount")
                            (reset-game-lobby-state))
    :component-did-mount (fn [_]
                           (log "game lobby did mount"))}))

(defn fig-reload []
  (.log js/console "game lobby figwheel reloaded! ")
  (mount-dom #'game-lobby))

(defn ^:export run []
  (init-game-lobby)
  (mount-dom #'game-lobby))
