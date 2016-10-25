(ns multiplayer-online-battle.utils
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan close!]]
            [ajax.core :refer [GET POST]]
            [reagent.core :as r :refer [atom]]
            [reagent.debug :refer [dbg log prn]]
            [multiplayer-online-battle.comm :refer [ws-chan]]))

(enable-console-print!)

(declare ws-in)
(declare ws-out)

(let [{:keys [ws-in ws-out]} (ws-chan)]
    (def ws-in ws-in)
    (def ws-out ws-out))

;; (defn boot-ws []
;;   (let [{:keys [ws-in ws-out]} (ws-chan)]
;;     (def ws-in ws-in)
;;     (def ws-out ws-out)))

(defn mount-dom [dom]

  (if-let [app-dom (.getElementById js/document "app")]
    (r/render-component
     [dom]
     app-dom)))

(defn ajax-call [fn url params] 
  (let [out (chan)]
    (fn url {:params params
             :handler #(>! out %)
             :error-handler #()
             :format :json
             :response-format :json})
    out))


