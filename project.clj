(defproject multiplayer-online-battle "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [org.clojure/core.async "0.2.391"
                  :exclusions [org.clojure/tools.reader]]

                 ;;http server
                 [http-kit "2.2.0"]

                 ;;routing
                 [compojure "1.5.1"]

                 ;;library for web app
                 [ring "1.5.0"]
                 [ring/ring-defaults "0.2.1"]

                 ;;websocket
                 [com.taoensso/sente "1.11.0"]
                 [com.taoensso/timbre "4.7.4"]

                 ;;logging
                 [org.clojure/tools.logging "0.3.1"]

                 ;;front-end
                 [reagent "0.6.0"]
                 [cljs-ajax "0.5.8"]]

  :plugins [[lein-figwheel "0.5.8"]
            [lein-cljsbuild "1.1.4" :exclusions [[org.clojure/clojure]]]
            [lein-ring "0.9.7"]]

  ;;allow lein run to find a entry point to start
  :main multiplayer-online-battle.main

  ;;source code paths
  :source-paths ["src_server", "src_cljs"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {:builds
              [{:id "landing"
                :source-paths ["src_cljs"]
                :jar true
                ;; the presence of a :figwheel configuration here
                ;; will cause figwheel to inject the figwheel client
                ;; into your build
                :figwheel {:on-jsload "multiplayer-online-battle.landing/fig-reload"
                           ;; :open-urls will pop open your application
                           ;; in the default browser once Figwheel has
                           ;; started and complied your application.
                           ;; Comment this out once it no longer serves you.
                           :open-urls ["http://localhost:8080/index.html"]}

                :compiler {:main multiplayer-online-battle.landing
                           :asset-path "js/compiled/out_landing"
                           :output-to "resources/public/js/compiled/multiplayer_online_battle_landing.js"
                           :output-dir "resources/public/js/compiled/out_landing"
                           :source-map-timestamp true
                           
                           ;; To console.log CLJS data-structures make sure you enable devtools in Chrome
                           ;; https://github.com/binaryage/cljs-devtools
                           :preloads [devtools.preload]
                           }}
               {:id "game-lobby"
                :source-paths ["src_cljs"]
                :jar true
                ;; the presence of a :figwheel configuration here
                ;; will cause figwheel to inject the figwheel client
                ;; into your build
                :figwheel {:on-jsload "multiplayer-online-battle.game-lobby/fig-reload"
                           ;; :open-urls will pop open your application
                           ;; in the default browser once Figwheel has
                           ;; started and complied your application.
                           ;; Comment this out once it no longer serves you.
                           :open-urls ["http://localhost:8080/gamelobby.html"]}

                :compiler {:main multiplayer-online-battle.game-lobby
                           :asset-path "js/compiled/out_game_lobby"
                           :output-to "resources/public/js/compiled/multiplayer_online_battle_game_lobby.js"
                           :output-dir "resources/public/js/compiled/out_game_lobby"
                           :source-map-timestamp true
                           ;; To console.log CLJS data-structures make sure you enable devtools in Chrome
                           ;; https://github.com/binaryage/cljs-devtools
                           :preloads [devtools.preload]
                           }}
               {:id "gaming"
                :source-paths ["src_cljs"]
                :jar true
                ;; the presence of a :figwheel configuration here
                ;; will cause figwheel to inject the figwheel client
                ;; into your build
                :figwheel {:on-jsload "multiplayer-online-battle.game-control/fig-reload"
                           ;; :open-urls will pop open your application
                           ;; in the default browser once Figwheel has
                           ;; started and complied your application.
                           ;; Comment this out once it no longer serves you.
                           :open-urls ["http://localhost:8080/gaming.html"]}

                :compiler {:main multiplayer-online-battle.game-control
                           :asset-path "js/compiled/out_game_control"
                           :output-to "resources/public/js/compiled/multiplayer_online_battle_game_control.js"
                           :output-dir "resources/public/js/compiled/out_game_control"
                           :source-map-timestamp true
                           ;; To console.log CLJS data-structures make sure you enable devtools in Chrome
                           ;; https://github.com/binaryage/cljs-devtools
                           :preloads [devtools.preload]
                           }}
               ;; This next build is an compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "landing_min"
                :source-paths ["src_cljs"]
                :compiler {:output-to "resources/public/js/compiled/multiplayer_online_battle_landing_min.js"
                           :main multiplayer-online-battle.landing
                           :optimizations :advanced
                           :pretty-print false}}
               {:id "game_lobby_min"
                :source-paths ["src_cljs"]
                :compiler {:output-to "resources/public/js/compiled/multiplayer_online_battle_game_lobby_min.js"
                           :main multiplayer-online-battle.game-lobby
                           :externs ["resources/public/js/rx.all.min.js"]
                           :optimizations :advanced
                           :pretty-print false}}
               {:id "gaming_min"
                :source-paths ["src_cljs"]
                :compiler {:output-to "resources/public/js/compiled/multiplayer_online_battle_game_control_min.js"
                           :main multiplayer-online-battle.game-control
                           :externs ["resources/public/js/rx.all.min.js"]
                           :optimizations :advanced
                           :pretty-print false}}
               ]}

  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this

             ;; doesn't work for you just run your own server :) (see lein-ring)

             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you are using emacsclient you can just use
             ;; :open-file-command "emacsclient"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             }


  ;; setting up nREPL for Figwheel and ClojureScript dev
  ;; Please see:
  ;; https://github.com/bhauman/lein-figwheel/wiki/Using-the-Figwheel-REPL-within-NRepl

  :uberjar-name "multiplayer-online-battle-standalone.jar"
  :profiles {:dev {:dependencies [[binaryage/devtools "0.8.2"]
                                  [figwheel-sidecar "0.5.8"]
                                  [com.cemerick/piggieback "0.2.1"]]
                   ;; need to add dev source path here to get user.clj loaded
                   :source-paths ["src_cljs" "dev"]
                   ;; for CIDER
                   ;; :plugins [[cider/cider-nrepl "0.12.0"]]
                   :repl-options {; for nREPL dev you really need to limit output
                                  ;:init (set! *print-length* 50)
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}
             :uberjar {:aot :all
                       :prep-tasks ["compile" ["cljsbuild" "once" "landing_min" "game_lobby_min" "gaming_min"]]
                       }})
