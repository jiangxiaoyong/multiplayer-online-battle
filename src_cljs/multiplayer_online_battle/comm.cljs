(ns multiplayer-online-battle.comm
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! put! take! chan close! alts! timeout]]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [reagent.debug :refer [dbg log prn]]))

(enable-console-print!)

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
       {:type :auto ; e/o #{:auto :ajax :ws}
       })]
  (def chsk        chsk)
  (def ch-recv    ch-recv) ; ChannelSocket's receive channel
  (def send-fn   send-fn) ; ChannelSocket's send API fn
  (def chsk-state state))

(let [ws-out (chan)]
  (go-loop []
    (let [msg (<! ws-out)] 
      (send-fn msg))
    (recur))
  (def ws-out ws-out))

(let [ws-in (chan)]
  (go-loop []
    (let [msg (<! ch-recv)]
      (>! ws-in msg))
    (recur))
  (def ws-in ws-in))

