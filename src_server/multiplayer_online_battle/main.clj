(ns multiplayer-online-battle.main
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [clojure.pprint :refer [pprint]]
            [multiplayer-online-battle.websocket :refer [start-websocket]]
            [multiplayer-online-battle.events-router :refer [start-events-router]]
            [multiplayer-online-battle.server :refer [start-web-server stop-web-server]]))

(defn start
  []
  (start-websocket)
  (start-events-router)
  (start-web-server))

(defn -main [& args] (start))
