(ns multiplayer-online-battle.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [ajax.core :refer [GET POST]]
            [cljs.core.async :refer [<! >! put! take! chan close! timeout]]
            [multiplayer-online-battle.landing :refer [register-user-info]]
            [multiplayer-online-battle.utils :refer [ajax-call]]
            [multiplayer-online-battle.comm :refer [ws-chan]]
            [multiplayer-online-battle.game-loby :refer [game-loby]]))

(enable-console-print!)

(def component-attr (r/atom {}))
(reset! component-attr {:game-loby {:visibility "none" :animate "animated fadeInDown"} 
                        :landing-pg {:visibility "" :animate "animated fadeInDown"}})

(defn app []
  (let [{:keys [ch-in ch-out]} (ws-chan)]
    (r/create-class
     {:component-will-mount (fn [_]
                              (log "app component will mount"))
      :component-did-mount (fn [_]
                             (log "app component did mount"))
      :component-will-unmount (fn [_]
                                (log "app component will Unmount"))
      :reagent-render (fn []
                        [:div
                         [game-loby ch-in ch-out component-attr]
                         [register-user-info ch-out component-attr]])})))

;;for figwheel auto reload
;; (r/render-component [app]
;;                     (. js/document (getElementById "app")))

(defn mount-dom []
  (if-let [app-dom (.getElementById js/document "app")]
    (r/render-component
     [app]
     app-dom)))

(defn fig-reload []
  (.log js/console "figwheel reloaded! ")
  (mount-dom))

(defn ^:export run []
  (mount-dom))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
