(ns multiplayer-online-battle.core
  (:require [reagent.core :as r :refer [atom]]))

(enable-console-print!)

(println "This text is printed from src/multiplayer-online-battle/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))

(defn collect-user-info []
  [:div.container
   [:div.row
    [:div.col-md-6.col-md-offset-3
     [:div.panel.panel-login
      [:div.panel-body
       [:div.row
        [:div.col-lg-12
         [:form#register-form
          [:h2
           [:center "Multiple Online Battle Arena"]]
          [:div.form-group
           [:input {:type "text" :name "username" :id "username" :class "form-control" :placeholder "Username" :value ""}]]
          [:div.form-group
           [:div.row
            [:div.col-sm-6.col-sm-offset-3
             [:input {:type "submit" :name "register-submit" :id "register-submit" :class "form-control btn btn-register" :value "Confirm"}]]]]]]]]]]]])

(defn fun []
  [:div
   [:ul
    [:li "FFFFF"]]])

(defn app []
  (r/create-class
   {:component-will-mount (fn [_]
                            (js/console.log "app component will mount"))
    :component-did-mount (fn [_]
                           (js/console.log "app component did mount"))
    :component-will-unmount (fn [_]
                              (js/console.log "app component will Unmount"))
    :reagent-render (fn [_]
                      [collect-user-info])}))

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
