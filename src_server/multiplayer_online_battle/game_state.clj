(ns multiplayer-online-battle.game-state
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            ))

(def players-init-state {:all-players-ready false})
(def players (atom players-init-state))
