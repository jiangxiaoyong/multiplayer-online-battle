(ns multiplayer-online-battle.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]
            [cljs.core.async :refer [<! >! put! take! chan close! timeout]]
            [multiplayer-online-battle.login :refer [register-user-info]]
            [multiplayer-online-battle.utils :refer [ajax-call debug-info]]))

(enable-console-print!)

(println "This text is printed from src/multiplayer-online-battle/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(defn app []
  (r/create-class
   {:component-will-mount (fn [_]
                            (js/console.log "app component will mount"))
    :component-did-mount (fn [_]
                           (js/console.log "app component did mount"))
    :component-will-unmount (fn [_]
                              (js/console.log "app component will Unmount"))
    :reagent-render (fn [_]
                      (register-user-info))}))

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
