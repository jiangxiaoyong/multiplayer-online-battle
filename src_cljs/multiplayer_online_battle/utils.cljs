(ns multiplayer-online-battle.utils
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [ajax.core :refer [GET POST]]
            [reagent.core :as r :refer [atom]]))

(enable-console-print!)

(defn mount-dom [dom]
  (if-let [app-dom (.getElementById js/document "app")]
    (r/render-component
     [dom]
     app-dom)))

(defn ajax-call [fn url params] 
  (debug-info "ajax-call paramter" params)
  (let [out (chan)]
    (fn url {:params params
             :handler #(>! out %)
             :error-handler #()
             :format :json
             :response-format :json})
    out))


