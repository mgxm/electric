(ns hyperfiddle.popover
  (:require [hyperfiddle.api :as hf]
            [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :as dom]
            [hyperfiddle.photon-ui2 :as ui])
  (:import [hyperfiddle.photon Pending])
  #?(:cljs (:require-macros hyperfiddle.popover)))

; data PopoverState = Closed | Open request | Pending request
; data BodyState = Idle | Request command | Pending command
; data Command = Commit tx | Discard

(p/defn PopoverBody [Body]
  (dom/div {:style {:position "fixed"}}
    (dom/div {:style {:border           "1px pink solid" :padding "5px"
                      :position         "relative" :left "3em" :top "2em" :z-index "1"
                      :width            "50em" :height "40em"
                      :background-color "rgb(248 250 252)"}}
      (let [!stage (atom ::unknown) stage (p/watch !stage)]

        (p/with-cycle [loading ::hf/loading]
          (binding [hf/loading loading]
            (dom/div (pr-str (name loading))) ; todo distributed glitch
            (try

              (let [stage (p/server
                            (p/with-cycle [stage []]
                              (binding [hf/db (:db-after (hf/with hf/db stage))]
                                (p/client (Body.)))))] ;; TODO validation
                (reset! !stage stage))

              ::hf/idle
              (catch Pending e ::hf/loading))))

        (dom/hr)
        (let [commit (when (ui/Button. "commit!" (not= hf/loading ::hf/idle)) stage) ;; TODO disable when invalid
              discard (when (ui/Button. "discard" (not= hf/loading ::hf/idle)) [])]
          (ui/edn-editor stage {::dom/disabled true})
          (or commit discard))))))

(p/defn Popover [label Body]
  (:request
    (p/with-cycle [{:keys [status]} {:status :closed}]
      (let [toggle (ui/Button. label (= :pending status)) ; popover anchor
            request (case status
                      :closed nil
                      (:open :pending) (PopoverBody. Body))]
        {:status (case status
                   :closed (if toggle :open :closed)
                   :open (case request
                           nil (if toggle :closed :open)
                           :pending)

                   ; todo what happens if the parent load fails due to concurrent modification
                   :pending (case hf/loading
                              ::hf/idle :closed ; close when loading is finished
                              :pending))
         :request request}))))