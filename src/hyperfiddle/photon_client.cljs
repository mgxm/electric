(ns hyperfiddle.photon-client
  (:require [cognitect.transit :as t]
            [com.cognitect.transit.types]
            [hyperfiddle.logger :as log]
            [missionary.core :as m]
            ["reconnecting-websocket" :as ReconnectingWebSocket]
            ))

(defn heartbeat! [interval socket]
  (js/setTimeout (fn rec []
                   (when (= 1 (.-readyState socket))
                     (.send socket "heartbeat")
                     (js/setTimeout rec interval)))))

(defn server-url []
  (let [url (.. js/window -hyperfiddle_photon_config -server_url)]
    (assert (string? url) "Missing websocket server url.")
    url))
 
(defn encode "Serialize to transit json" [v] (t/write (t/writer :json) v))
(defn decode "Parse transit json" [^String s] (t/read (t/reader :json) s))

(extend-type com.cognitect.transit.types/UUID IUUID) ; https://github.com/hyperfiddle/hyperfiddle/issues/728

;; TODO reconnect on failures
(defn connect [cb]
  (fn [s f]
    (let [client-id   (random-uuid)
          max-retries 4
          socket      (new ReconnectingWebSocket
                           (str (server-url) "?client-id=" client-id)
                           #js[] #js{:maxRetries (inc max-retries)})
          on-open     (fn on-open [_]
                        (heartbeat! 30000 socket)
                        (.removeEventListener socket on-open)
                        (set! (.-onmessage socket)
                              #(let [decoded (decode (.-data %))]
                                 (log/trace "🔽" decoded)
                                 (cb decoded)))
                        (s (fn
                             ([] (.close socket))
                             ([x]
                              (fn [s f]
                                (try
                                  (log/trace "🔼" x)
                                  (.send socket (encode x))
                                  (s nil)
                                  (catch :default e
                                    (log/error "Failed to write on socket" e)
                                    (f e)))
                                #(log/info "Canceling websocket write")))))
                        (some-> js/document (.getElementById "main") (.setAttribute "data-ws-connected" "true")))
          on-close    (fn [socket]
                        (log/debug "WS close" socket)
                        (some-> js/document (.getElementById "main") (.setAttribute "data-ws-connected" "false")))
          on-error    (fn [err]
                        (if (> (.-retryCount socket) max-retries)
                          (when (js/confirm "Socket failed to reconnect, please refresh.")
                            (.close socket)
                            (f err))
                          (log/debug "WS error" err)))]
      (.addEventListener socket "open" on-open)
      (.addEventListener socket "close" on-close)
      (.addEventListener socket "error" on-error)
      #(prn "TODO Cancel connection in connecting state"))))

(defn client [[c s]]
  (m/sp
    (let [m (m/mbx)
          write-chan (m/? (connect m))]
      (try (m/? (write-chan s))
           (m/? (c write-chan m))
           (catch :default err
             (write-chan) ; close channel socket
             (throw err)
             )))))