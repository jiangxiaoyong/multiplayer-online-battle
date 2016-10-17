(ns multiplayer-online-battle.login
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [clojure.string :as str]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]
            [multiplayer-online-battle.utils :refer [ajax-call debug-info]]))

(enable-console-print!)

(def input-val (r/atom ""))

(defn register-user-info []
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
           [:input {:type "text" 
                    :id "username" 
                    :class "form-control" 
                    :placeholder "Username" 
                    :value @input-val
                    :on-change #(reset! input-val (-> % .-target .-value))}]]
          [:div.form-group
           [:div.row
            [:div.col-sm-6.col-sm-offset-3
             [:input {:type "submit" 
                      :name "register-submit"
                      :id "register-submit"
                      :class "form-control btn btn-register"
                      :value "Confirm"
                      :disabled (if (str/blank? @input-val)
                                  true
                                  false)
                      :on-click #(ajax-call POST "/register-user-info" {:username @input-val})}]]]]]]]]]]]])
