(ns multiplayer-online-battle.landing
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [clojure.string :as str]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]
            [multiplayer-online-battle.utils :refer [ajax-call debug-info]]
            [multiplayer-online-battle.comm :as comm]))

(enable-console-print!)

(defn register-user-info [ch-out component-attr]
  (let [input-val (r/atom "")]
    (fn []
      [:div.container {:class (get-in @component-attr [:landing-pg :animate]) 
                       :style {:display (get-in @component-attr [:landing-pg :visibility])}}
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
                          :on-click #(do
                                       (go
                                         (>! ch-out [:register-user/username {:username @input-val}]))
                                       (swap! component-attr assoc-in [:landing-pg :animate] "animated fadeOutDown")
                                       (swap! component-attr assoc-in [:game-loby :visibility] ""))}]]]]]]]]]]]])))
