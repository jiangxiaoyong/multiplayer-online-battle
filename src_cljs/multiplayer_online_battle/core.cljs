(ns multiplayer-online-battle.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]
            [cljs.core.async :refer [<! >! put! take! chan close! timeout]]
            [clojure.pprint :refer [pprint]]
            [multiplayer-online-battle.landing :refer [register-user-info]]
            [multiplayer-online-battle.utils :refer [ajax-call]]
            [multiplayer-online-battle.comm :refer [ws-chan]]))

(enable-console-print!)

(def component-attr (r/atom {}))
(reset! component-attr {:game-loby {:visibility "none" :animate "animated fadeInDown"} 
                        :landing-pg {:visibility "" :animate "animated fadeInDown"}})

(defn app []
  (let [{:keys [ch-in ch-out]} (ws-chan)]
    (r/create-class
     {:component-will-mount (fn [_]
                              (js/console.log "app component will mount"))
      :component-did-mount (fn [_]
                             (js/console.log "app component did mount"))
      :component-will-unmount (fn [_]
                                (js/console.log "app component will Unmount"))
      :reagent-render (fn [_]
                        (register-user-info ch-out component-attr))})))

(defn mount-app []
  (if-let [app-dom (.getElementById js/document "app")]
    (r/render-component
     [app]
     app-dom)))

(defn ^:export run []
  (mount-app))

;;for figwheel auto reload
(r/render-component [app]
                    (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
