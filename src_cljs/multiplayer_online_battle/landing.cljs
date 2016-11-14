(ns multiplayer-online-battle.landing
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [clojure.string :as str]
            [ajax.core :refer [GET POST]]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [taoensso.sente  :as sente]
            [multiplayer-online-battle.states :refer [components-state]]))

(enable-console-print!)

(defn handler [res]
  (log "server response " res)
  (.assign js/window.location "/gamelobby"))

(defn error-handler [{:keys [status status-text response]}]
  (.log js/console (str "something bad happened: " status " " status-text " response" response)))

(defn register-user-info [component-state]
  (let [input-val (r/atom "")]
    (fn []
      [:div#landing-pg.container {:class (get-in @component-state [:landing-pg :animate])}
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
               [:input {:type "text" 
                        :id "username" 
                        :class "form-control" 
                        :placeholder "Username" 
                        :value @input-val
                        :on-change #(reset! input-val (-> % .-target .-value))}]]
              [:div.form-group
               [:div.row
                [:div.col-sm-6.col-sm-offset-3
                 [:input {:name "register-submit"
                          :id "register-submit"
                          :class "form-control btn btn-register"
                          :value "Confirm"
                          :disabled (if (str/blank? @input-val)
                                      true
                                      false)
                          :on-click #(let [formdata (doto
                                                      (js/FormData.)
                                                      (.append "user-name" @input-val))]
                                       (POST "/login" {:body formdata
                                                       :handler handler
                                                       :error-handler error-handler}))}]]]]]]]]]]]])))

(defn landing []
  (r/create-class
   {:component-will-mount (fn [_]
                            (log "app component will mount"))
    :component-did-mount (fn [_]
                           (log "app component did mount"))
    :component-will-unmount (fn [_]
                              (log "app component will Unmount"))
    :reagent-render (fn []
                      [register-user-info components-state])}))


