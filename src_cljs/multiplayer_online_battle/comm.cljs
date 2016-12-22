(ns multiplayer-online-battle.comm
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! put! take! chan close! alts! timeout]]
            [clojure.string :as str]
            [taoensso.sente  :as sente :refer (cb-success?)]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [reagent.debug :refer [dbg log prn]]
            [multiplayer-online-battle.states :refer [game-lobby-state]]))

(enable-console-print!)

;;;;;;;;;;;;;;; Define Sente channel socket client

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client! "/chsk" ; Note the same path as before
       {:type :ws ; e/o #{:auto :ajax :ws}
       })]
  (def chsk        chsk)
  (def ch-recv    ch-recv) ; ChannelSocket's receive channel
  (def send-fn   send-fn) ; ChannelSocket's send API fn
  (def chsk-state state))

(declare ws->lobby)
(declare ws->gaming)

(def chsk-ready? (chan))
(def cmd-msg-ch (chan))

;;;;;;;;;;;;; Set up Sente event handler
(defn where-to-route? [ev-type]
  (let [where? (fn [ev] 
                 (keyword (namespace ev)))]
    (cond
     (= :game-lobby (where? ev-type)) :game-lobby
     (= :gaming (where? ev-type)) :gaming
     :else :unknow)))

(defn start-ev-router []
  (let [ws->lobby (chan)
        ws->gaming (chan)]
    (go-loop []
      (let [ev-msg (<! ch-recv)
            {:as evmsg :keys [id ?data]} ev-msg]
        (cond
         (= id :chsk/state) (let [[old-state-map new-state-map] ?data]
                              (when (:first-open? new-state-map)
                                (swap! game-lobby-state update-in [:chsk-ready?] not)
                                (>! chsk-ready? (:chsk-ready? @game-lobby-state))
                                (infof "Channel socket succuessfully established! %s" new-state-map)
                                ))
         (= id :chsk/handshake) (let [[?uid ?handshake-data] ?data]
                                  (infof "Handshake: %s" ?data))
         (= id :chsk/recv) (let [ev-type (first ?data)
                                 route-to (where-to-route? ev-type)]
                             (infof "comm receiving %s" ?data)
                             (cond
                              (= route-to :game-lobby) (>! ws->lobby ?data)
                              (= route-to :gaming) (>! ws->gaming ?data)
                              :else "Unknow game event"))))
      (recur))
    {:ws->lobby ws->lobby
     :ws->gaming ws->gaming}))

;;;;;;;;;;;;;; Game lobby async channel

(defn game-lobby-ch []
  (let [game-lobby-in (chan)
        game-lobby-out (chan)]
    (go-loop []
      (let [[data ch] (alts! [ws->lobby game-lobby-out])]
        (cond
         (= ch ws->lobby) (do
                            (>! game-lobby-in data))
         (= ch game-lobby-out) (do
                                 (send-fn data))))
      (recur))
    {:game-lobby-in game-lobby-in
     :game-lobby-out game-lobby-out}))

;;;;;;;;;;;;;; Gaming async channel

(defn gaming-ch []
  (let [gaming-in (chan)
        gaming-out (chan)]
    (go-loop []
      (let [[data ch] (alts! [ws->gaming gaming-out])]
        (cond
         (= ch ws->gaming) (do
                            (>! gaming-in data))
         (= ch gaming-out) (do
                             (debugf "gaming channele sending %s" data)
                             (send-fn data))))
      (recur))
    {:gaming-in gaming-in
     :gaming-out gaming-out}))

(let [{:keys [gaming-in gaming-out]} (gaming-ch)]
  (def gaming-in gaming-in)
  (def gaming-out gaming-out))

;;;;;;;;;;;;;;; Define Sente event handlers

;; (defmulti event-msg-handler :id)

;; (defmethod event-msg-handler :default
;;   [{:as ev-msg :keys [event]}]
;;   (infof  "unhandlered event: %s" event))
  
;; (defmethod event-msg-handler :chsk/state
;;   [{:as ev-msg :keys [?data]}]
;;   (let [[old-state-map new-state-map] ?data]
;;     (if (:first-open? new-state-map)
;;       (infof "Channel socket succuessfully established! %s" new-state-map)
;;       (infof "Channel socket state change: %s" new-state-map))))

;; (defmethod event-msg-handler :chsk/handshake
;;   [{:as ev-msg :keys [?data]}]
;;   (let [[?uid ?handshake-data] ?data]
;;     (infof "Handshake: %s " ?data)))

;; (defmethod event-msg-handler :chsk/recv
;;   [{:as ev-msg :keys [?data]}])

;;;;;;;;;;;;;;; Set up Sente event router

;; (defonce ev-router (atom nil))
;; (defn stop-ev-router [] (when-let [stop-f @ev-router] (stop-f)))
;; (defn start-ev-router []
;;   (stop-ev-router)
;;   (log "Starting client ws event router !")
;;   (reset! ev-router
;;     (sente/start-chsk-router!
;;      ch-recv event-msg-handler)))

;;;;;;;;;;;;;;;; Sente utils

(defn reconnect []
  (log "Reconnecting")
  (sente/chsk-reconnect! chsk))

(defn disconnect []
  (log "Disconnecting")
  (sente/chsk-disconnect! chsk))

(defn start-comm []
  (let [{:keys [ws->lobby ws->gaming]} (start-ev-router)]
    (def ws->lobby ws->lobby)
    (def ws->gaming ws->gaming)))
