(ns multiplayer-online-battle.utils
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [ajax.core :refer [GET POST]]))

(enable-console-print!)

(defn debug-info [desc, content]
  (->> (str "DEBUG :" " -- " desc " -- " content)
       (print)))

(defn ajax-call [fn url params] 
  (debug-info "ajax-call paramter" params)
  (let [out (chan)]
    (fn url {:params params
             :handler #(>! out %)
             :error-handler #()
             :format :json
             :response-format :json})
    out))


