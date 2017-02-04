(ns multiplayer-online-battle.game-control
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan sliding-buffer put! close! timeout pub sub]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [components-state game-lobby-state world flap-starting-state pillar-buf]]
            [multiplayer-online-battle.flappy-bird :refer [animation-loop flappy-bird-ui]]
            [multiplayer-online-battle.utils :refer [mount-dom ev-msg]]
            [multiplayer-online-battle.network :refer [init-network network-ch-in chsk-ready?]]
            [multiplayer-online-battle.reactive :refer [reactive-publication]]))

;;; gaming control ;;;

(def gaming-ev-ch (chan))

(defn sub-gaming-ev []
  (sub reactive-publication :gaming-ev gaming-ev-ch))

(def jump-step 6)

(defn jump [{:keys [jump-count] :as state} cur-time]
  (infof "jump!")
  (-> state
      (assoc
          :jump-count (inc jump-count)
          :jump-start-time cur-time
          :jump-step jump-step)))

(defn reset-state [time-stamp]
  (-> @world
      (update-in [:pillar-list] (fn [pls] (map #(assoc % :start-time time-stamp) pls)))
      (update-in [:all-players] (fn [pls] (into {} (map #(assoc-in % [1 :start-time] time-stamp) pls))))
      (update-in [:all-players] (fn [pls] (into {} (map #(assoc-in % [1 :jump-start-time] time-stamp) pls))))
      (assoc-in [:winner] nil)
      (assoc-in [:start-time] time-stamp)))

(defn start-game []
  (.requestAnimationFrame
   js/window
   (fn [time-stamp]
     (infof "Start Game" )
     (reset! world (reset-state time-stamp))
     (animation-loop time-stamp))))

(defn send-to-network [{:keys [ev payload]}]
  (go
    (>! network-ch-in (ev-msg ev payload))))

(defn cmd-msg-stream-handler [cmd-msg-stream]
  (print "cmd-msg" cmd-msg-stream)
  (doseq [msg cmd-msg-stream]
    (let [key-type (:key-type msg)
          key-code (:key-code msg)
          player-id (:player-id msg)
          cur-time (:cur-time @world)]
      (swap! world update-in [:all-players player-id] jump cur-time))))

(defn- construct-all-flappy-state [p-ids p-info]
  (loop [ids p-ids
         info p-info
         states (map #(assoc flap-starting-state :flappy-x %) (->> (iterate #(+ 80 %) 212)
                                                                   (take (count ids))))]
    (when-not (empty? ids)
      (swap! world assoc-in [:all-players (first ids)] (merge (first states) (first info)))
      (recur (rest ids) (rest info) (rest states)))))

(defn init-gaming-ev-handler []
  (sub-gaming-ev)
  (go-loop []
    (let [content (:content (<! gaming-ev-ch))
          ev (:ev content)
          payload (:payload content)
          payload-val (vals payload)
          payload-keys (keys payload)
          who (first (keys payload))]
      (case ev
         :redirect (.assign js/window.location (:dest payload))
         :player-current (swap! world assoc :player-current payload)
         :players-all (do
                        (construct-all-flappy-state payload-keys payload-val)
                        (swap! world assoc-in [:game-loaded?] true)
                        (send-to-network {:ev :gaming/game-loaded :payload {}}))
         :player-die (swap! world update-in [:all-players] (fn [pls] (into {} (remove #(= (first %) (:player-id payload)) pls))))
         :you-are-winner (swap! world assoc-in [:winner] (:player-id payload))
         :game-loaded (when (:all-game-loaded payload)
                        (swap! world assoc-in [:timer-running] true)
                        (swap! world assoc-in [:waiting-opponents] false)
                        (start-game))
         :new-pillar (swap! world assoc-in [:new-pillar-height] (:new-pillar payload))
         :cmd-msg (cmd-msg-stream-handler (first payload-val))
         :return-to-lobby (send-to-network {:ev :gaming/return-to-lobby :payload payload})
         :iam-dead (send-to-network {:ev :gaming/iam-dead :payload payload})
         :upload-cmd-msg (send-to-network {:ev :gaming/command :payload payload})
       ))
    (recur)))

(defn load-gaming-state []
  (go
    (let [ready (<! chsk-ready?)]
      (when ready
        (send-to-network {:ev :gaming/states :payload {}})))))

;;; game-lobby-control ;;;

(defn player-exist? [id]
  (if (contains? (:players-all @game-lobby-state) id)
    true
    false))

(def game-lobby-ev-ch (chan))

(defn sub-game-lobby-ev []
  (sub reactive-publication :game-lobby-ev game-lobby-ev-ch))

(defn init-game-lobby-ev-handler []
  (sub-game-lobby-ev)
  (go-loop []
    (let [content (:content (<! game-lobby-ev-ch))
          ev (:ev content)
          payload (:payload content)
          payload-val (vals payload)
          payload-keys (keys payload)
          who (first (keys payload))]
      (infof "game-lobby receiving event %s" content)
      (case ev
        :player-current (swap! game-lobby-state assoc :player-current (first payload-val))
        :players-all (swap! game-lobby-state assoc :players-all payload)
        :player-come (swap! game-lobby-state update-in [:players-all] conj payload)
        :player-update (swap! game-lobby-state assoc-in [:players-all who :status] (:status (first payload-val)))
        :player-leave (swap! game-lobby-state update-in [:players-all] (fn [pls] (into {} (remove #(= (first %) (first payload-keys)) pls))))
        :player-ready (send-to-network {:ev :game-lobby/player-ready :payload payload})
        :all-players-ready (swap! game-lobby-state update-in [:all-players-ready] not)
        :pre-enter-game-count-down (swap! components-state assoc-in [:game-lobby :style :btn-ready-label] (:count payload))
        :pre-enter-game-dest (.assign js/window.location (:dest payload))))
    (recur)))

(defn load-game-lobby-state []
  (go
    (let [ready (<! chsk-ready?)]
      (when ready
        (send-to-network {:ev :game-lobby/states :payload {}})))))

;;; init fn ;;;

(defn init-gaming []
  (init-network)
  (init-gaming-ev-handler)
  (load-gaming-state))

(defn init-game-lobby []
  (init-network)
  (init-game-lobby-ev-handler)
  (load-game-lobby-state))

(defn fig-reload []
  (.log js/console "figwheel reloaded! ")
  (mount-dom #'flappy-bird-ui))

(defn ^:export run []
  (init-gaming)
  (mount-dom #'flappy-bird-ui))
