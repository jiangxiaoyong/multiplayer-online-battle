(ns multiplayer-online-battle.game-state
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            ))

(def players (atom []))
