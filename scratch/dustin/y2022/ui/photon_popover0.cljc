(ns dustin.y2022.photon-popover0
  (:require #?(:clj [datomic.api :as d])
            #?(:cljs goog.events)
            [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [hyperfiddle.photon-ui :as ui]
            [hyperfiddle.rcf :refer [tests tap % with]]
            [missionary.core :as m])
  #?(:cljs (:require-macros dustin.y2022.photon-popover0)))

(p/def db)

; data PopoverState = Closed Tx | Open
(p/defn Popover [Body X] ; only client
  (let [!open (atom false)
        open? (p/watch !open)
        !ret (atom nil)]

    ; popover anchor
    (ui/button {::ui/click-event (p/fn [_] (reset! !ret nil) (swap! !open not))}
               (if open? "close" "open") " popover")

    ; popover body
    (if-not open?
      (p/watch !ret) ; return tx on close
      (dom/div {:style {:position "fixed"}}
        (dom/div {:style {:border "1px pink solid" :padding "5px"
                          :position "relative" :left "3em" :top "2em" :z-index "1"
                          :width "50em" :height "40em"
                          :background-color "rgb(248 250 252)"}}
          ; discard, commit
          (Body. (fn Commit! [tx] (println `b tx) (reset! !ret tx) (reset! !open false))
                 (p/fn Discard! [] (reset! !open false))
                 X)))))) ; client bias, careful

(p/defn StagedBody [Commit! Discard! Body]
  (p/server
    (let [!stage (atom []), stage (p/watch !stage)] ; fork
      (binding [db (:db-after (d/with db stage))
                stage! (partial swap! !stage concat)]
        (p/client
          (Body.)
          (dom/hr)
          (ui/button {::ui/click-event (p/fn [e] (println `a stage) (Commit! stage))} "commit!")
          (ui/button {::ui/click-event (p/fn [e] (Discard!.))} "discard")
          (p/server (StagingArea. stage !stage)))))))

(p/defn App []
  (p/with-cycle [stage []]
    (binding [db (:db-after (d/with (p/watch !conn) stage))]
      (let [tx (Popover. StagedBody Teeshirt-orders-view)]
        (concat stage tx)))))