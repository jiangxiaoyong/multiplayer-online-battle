(ns multiplayer-online-battle.game-state
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            ))

(def players-init-state {:all-players-ready false
                         :in-battle? false
                         :all-players {}})
(def players (atom players-init-state))

(defn reset-game []
  (reset! players players-init-state))
