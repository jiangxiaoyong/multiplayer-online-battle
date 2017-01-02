(ns multiplayer-online-battle.game-control
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan sliding-buffer put! close! timeout pub sub]]
            [taoensso.timbre :as timbre :refer (tracef debugf infof warnf errorf)]
            [multiplayer-online-battle.states :refer [components-state game-lobby-state world flap-starting-state pillar-buf]]
            [multiplayer-online-battle.flappy-bird :refer [animation-loop flappy-bird-ui]]
            [multiplayer-online-battle.utils :refer [mount-dom ev-msg]]
            [multiplayer-online-battle.network :refer [init-network network-ch-in chsk-ready?]]
            [multiplayer-online-battle.reactive :refer [reactive-publication]]))

;;;;;;;;;;;;;;;;;;;; gaming control

(def gaming-ev-ch (chan))
(def start-game? (chan))
(def game-loaded? (chan))

(defn sub-gaming-ev []
  (sub reactive-publication :gaming-ev gaming-ev-ch))

(def jump-step 7)

(defn jump [{:keys [jump-count] :as state} cur-time]
  (infof "jump!")
  (-> state
      (assoc
          :jump-count (inc jump-count)
          :jump-start-time cur-time
          :jump-step jump-step)))

(defn cmd-msg-stream-handler [cmd-msg-stream]
  (print "cmd-msg" cmd-msg-stream)
  (doseq [msg cmd-msg-stream]
    (let [key-type (:key-type msg)
          key-code (:key-code msg)
          player-id (:player-id msg)
          cur-time (:cur-time @world)]
      (swap! world update-in [:all-players player-id] jump cur-time))))

;; (defn init-subscribe->reactive []
;;   (sub-reactive)
;;   (go-loop []
;;     (let [content (:content (<! subscribe->reactive))
;;           ev (:ev content)
;;           data (:payload content)]
;;       (cond
;;        (= ev :return-to-lobby) (go
;;                                  (>! network-ch-in data))
;;        (= ev :upload-cmd-msg) (go
;;                                 (>! network-ch-in data))
;;        (= ev :cmd-msg-stream) (handle-cmd-msg-stream data)
;;        (= ev :upload-player-state) (go
;;                                      (>! network-ch-in data))))
;;     (recur)))

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
      (cond
       (= ev :redirect) (.assign js/window.location (:dest payload))
       (= ev :player-current) (swap! world assoc :player-current payload)
       (= ev :players-all) (do
                             (construct-all-flappy-state payload-keys payload-val)
                             (swap! world assoc-in [:game-loaded?] true)
                             (go (>! game-loaded? true)))
       (= ev :player-die) (swap! world update-in [:all-players] (fn [pls] (into {} (remove #(= (first %) (:player-id payload)) pls))))
       (= ev :you-are-winner) (swap! world assoc-in [:winner] (:player-id payload))
       (= ev :game-loaded) (when (:all-game-loaded payload)
                             (swap! world assoc-in [:timer-running] true)
                             (go
                               (>! start-game? true)))
       (= ev :new-pillar) (swap! world assoc-in [:new-pillar-height] (:new-pillar payload))
       (= ev :cmd-msg) (cmd-msg-stream-handler (first payload-val))
       (= ev :return-to-lobby) (go (>! network-ch-in (ev-msg :gaming/return-to-lobby payload)))
       (= ev :iam-dead) (go (>! network-ch-in (ev-msg :gaming/iam-dead payload)))
       (= ev :upload-cmd-msg) (go (>! network-ch-in (ev-msg :gaming/command payload)))
       ))
    (recur)))

(defn load-gaming-state []
  (go
    (let [ready (<! chsk-ready?)]
      (when ready
        (>! network-ch-in (ev-msg :gaming/states {}))))))

(defn gaming-loaded-notice []
  (go
    (let [loaded (<! game-loaded?)]
      (when loaded
        (>! network-ch-in (ev-msg :gaming/game-loaded {}))))))

(defn reset-state [time-stamp]
  (-> @world
      (update-in [:pillar-list] (fn [pls] (map #(assoc % :start-time time-stamp) pls)))
      (update-in [:all-players] (fn [pls] (into {} (map #(assoc-in % [1 :start-time] time-stamp) pls))))
      (update-in [:all-players] (fn [pls] (into {} (map #(assoc-in % [1 :jump-start-time] time-stamp) pls))))
      (assoc-in [:winner] nil)))

(defn start-game []
  (.requestAnimationFrame
   js/window
   (fn [time-stamp]
     (infof "Start Game" )
     (reset! world (reset-state time-stamp))
     (animation-loop time-stamp))))

(defn fire-game[]
  (go
    (let [fire (<! start-game?)]
      (when fire
        (start-game)))))

;;;;;;;;;;;;;;;;;;;; game-lobby-control

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
      (cond
       (= ev :player-current) (swap! game-lobby-state assoc :player-current (first payload-val))
       (= ev :players-all) (swap! game-lobby-state assoc :players-all payload)
       (= ev :player-come) (if-not (player-exist? who) (swap! game-lobby-state update-in [:players-all] conj payload))
       (= ev :player-update) (swap! game-lobby-state assoc-in [:players-all who :status] (:status (first payload-val)))
       (= ev :player-leave) (swap! game-lobby-state update-in [:players-all] (fn [pls] (into {} (remove #(= (first %) (first payload-keys)) pls))))
       (= ev :player-ready) (go (>! network-ch-in (ev-msg :game-lobby/player-ready payload)))
       (= ev :all-players-ready) (swap! game-lobby-state update-in [:all-players-ready] not)
       (= ev :pre-enter-game-count-down) (swap! components-state assoc-in [:game-lobby :style :btn-ready-label] (:count payload))
       (= ev :pre-enter-game-dest) (.assign js/window.location (:dest payload))))
    (recur)))

(defn load-game-lobby-state []
  (go
    (let [ready (<! chsk-ready?)]
      (when ready
        (>! network-ch-in (ev-msg :game-lobby/states {}))))))

;;; init fn ;;;

(defn init-gaming []
  (init-network)
  (init-gaming-ev-handler)
  (load-gaming-state)
  (gaming-loaded-notice)
  (fire-game))

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
