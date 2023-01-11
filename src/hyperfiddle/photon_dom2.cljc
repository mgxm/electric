(ns hyperfiddle.photon-dom2
  (:refer-clojure :exclude [time])
  (:require #?(:cljs goog.dom)
            #?(:cljs goog.object)
            #?(:cljs goog.style)
            [hyperfiddle.photon :as p]
            [hyperfiddle.photon-dom :refer [node ; reuse dom1/node for compat
                                            with class-str]]
            [hyperfiddle.rcf :as rcf :refer [tests]]
            [missionary.core :as m])
  (:import [hyperfiddle.photon Pending])
  #?(:cljs (:require-macros [hyperfiddle.photon-dom2 :refer [with]])))

#?(:cljs (defn dom-element* [parent type]
           (let [node (goog.dom/createElement type)]
             (.appendChild parent node) node)))

(defn hide-on-unmount [node]
  (m/observe (fn [!]
               (! nil)
               #(set! (.. node -style -display) "none"))))

(defmacro element [t & body]
  `(with (dom-element* node ~(name t))
     (new (hide-on-unmount node))
     ~@body))

#?(:cljs (defn text-node [parent]
           (let [node (goog.dom/createTextNode "")]
             (.appendChild parent node) node)))

#?(:cljs (defn -googDomSetTextContent [node & strs]
           (goog.dom/setTextContent node (apply str strs))))

(defmacro text [& strs]
  `(with (text-node node)
     (-googDomSetTextContent node ~@strs)))

#?(:cljs
   (defn set-property! [node k v]
     (let [k (name k)
           v (clj->js v)]
       (if (and (nil? v) (.hasAttribute node k))
         (.removeAttribute node k)
         (case k
           "style" (goog.style/setStyle node v)
           "class" (set! (.-className node) (class-str v))
           "for"   (set! (.-htmlFor node) v)
           "list"  (.setAttribute node k v) ; corner case, list (datalist) is setted by attribute and readonly as a prop.
           (if-let [k (goog.object/get goog.dom/DIRECT_ATTRIBUTE_MAP_ k)]
             (.setAttribute node k v)
             (if (goog.object/containsKey node k) ; is there an object property for this key?
               (goog.object/set node k v)
               (.setAttribute node k v))))))))

#?(:cljs (defn unmount-prop [node k v]
           (m/observe (fn [!] (! nil) #(set-property! node k v)))))

(defmacro style [m]
  (if (map? m)
    `(do ~@(mapcat (fn [[k v]] [`(set-property! node "style" {~k ~v})
                                `(new (unmount-prop node "style" {~k nil}))]) m)
         nil) ; static keyset
    `(p/for-by first [sty# (vec ~m)]
       (set-property! node "style" {(key sty#) (val sty#)})
       (new (unmount-prop node {(key sty#) nil}))
       nil)))

;; TODO JS runtimes intern litteral strings, so call `name` on keywords at
;; macroexpension.
(defmacro props [m]
  (let [style? #{:style ::style}]       ; TODO disambiguate
    (if (map? m)
      `(do ~@(mapcat (fn [[k v]] (if (style? k) ; static keyset
                                   [`(style ~v)]
                                   [`(set-property! node ~k ~v)
                                    `(new (unmount-prop node ~k nil))]))
                     m)
           nil)
      `(p/for-by key [prop# (vec ~m)]
         (if (~style? (key prop#))
           (style (val prop#))
           (do (set-property! node (key prop#) (val prop#))
               (new (unmount-prop node (key prop#) nil))
               nil))))))

(defn event* [dom-node event-name callback]
  (m/observe (fn [!]
               (! nil)
               (.addEventListener dom-node event-name callback)
               #(.removeEventListener dom-node event-name callback))))

(defmacro ^:deprecated event [event-name callback] `(new (event* node ~event-name ~callback)))

(defn happen [s e]
  ; Todo, we need a buffer to force a nil in between events to fix race
  (case (:status s)
    :idle {:status :impulse :event e} ; rising edge
    :pending {:status :impulse :event e} ; supersede the outstanding event with a new event
    :impulse (assert false "two events in the same frame? that's weird and wrong")))

; data EventState = Idle | Impulse event | Pending event
(p/defn Event [type busy]
  (:event
    (let [!state (atom {:status :idle})
          state (p/watch !state)]

      ; rising edge happens once, even if busy state (prevent infinite loop) -- [DJG] I don't understand
      (event type (partial swap! !state happen)) ; discrete rising edge

      (reset! !state
              (case (:status state)
                :idle state
                :impulse (assoc state :status :pending) ; impulse is seen for 1 frame and then cleared
                :pending (if busy state {:status :idle}))))))

(def <clock hyperfiddle.photon-dom/<clock)

(defmacro on
  ([typ]   `(new Event ~typ false))
  ([typ F] `(let [x# (p/with-cycle [?v# nil]
                       (let [busy# (= ?v# ::p/pending)]
                         (when-some [evt# (new Event ~typ busy#)]
                           (try (new ~F evt#) (catch Pending e# ::p/pending)))))]
              (case x# ::p/pending (throw (Pending.)) #_else x#))))

(defmacro on-pending [pending-body & body] `(try (do ~@body) (catch Pending e# ~pending-body (throw e#))))

(p/defn Focused? []
  (p/with-cycle [focused false]
    (if focused (nil? (on "blur")) (some? (on "focus")))))

(defn input-setter [node v] (set! (.-value node) (str v)))
(defmacro bind-value
  ([v]        `(bind-value ~v input-setter))
  ([v setter] `(let [v# ~v]
                 (when-not (new Focused?)
                   (case (new p/Unglitch v#) (~setter node v#))))))

(defmacro a [& body] `(element :a ~@body))
(defmacro abbr [& body] `(element :abbr ~@body))
(defmacro address [& body] `(element :address ~@body))
(defmacro area [& body] `(element :area ~@body))
(defmacro article [& body] `(element :article ~@body))
(defmacro aside [& body] `(element :aside ~@body))
(defmacro audio [& body] `(element :audio ~@body))
(defmacro b [& body] `(element :b ~@body))
(defmacro bdi [& body] `(element :bdi ~@body))
(defmacro bdo [& body] `(element :bdo ~@body))
(defmacro blockquote [& body] `(element :blockquote ~@body))
(defmacro br [& body] `(element :br ~@body))
(defmacro button [& body] `(element :button ~@body))
(defmacro canvas [& body] `(element :canvas ~@body))
(defmacro cite [& body] `(element :cite ~@body))
(defmacro code [& body] `(element :code ~@body))
(defmacro data [& body] `(element :data ~@body))
(defmacro datalist [& body] `(element :datalist ~@body))
(defmacro del [& body] `(element :del ~@body))
(defmacro details [& body] `(element :details ~@body))
(defmacro dfn [& body] `(element :dfn ~@body))
(defmacro dialog [& body] `(element :dialog ~@body))
(defmacro div [& body] `(element :div ~@body))
(defmacro dl "The <dl> HTML element represents a description list. The element encloses a list of groups of terms (specified using the <dt> element) and descriptions (provided by <dd> elements). Common uses for this element are to implement a glossary or to display metadata (a list of key-value pairs)." [& body] `(element :dl ~@body))
(defmacro dt "The <dt> HTML element specifies a term in a description or definition list, and as such must be used inside a <dl> element. It is usually followed by a <dd> element; however, multiple <dt> elements in a row indicate several terms that are all defined by the immediate next <dd> element." [& body] `(element :dt ~@body))
(defmacro dd "The <dd> HTML element provides the description, definition, or value for the preceding term (<dt>) in a description list (<dl>)." [& body] `(element :dd ~@body))
(defmacro em [& body] `(element :em ~@body))
(defmacro embed [& body] `(element :embed ~@body))
(defmacro fieldset [& body] `(element :fieldset ~@body))
(defmacro figure [& body] `(element :figure ~@body))
(defmacro footer [& body] `(element :footer ~@body))
(defmacro form [& body] `(element :form ~@body))
(defmacro h1 [& body] `(element :h1 ~@body))
(defmacro h2 [& body] `(element :h2 ~@body))
(defmacro h3 [& body] `(element :h3 ~@body))
(defmacro h4 [& body] `(element :h4 ~@body))
(defmacro h5 [& body] `(element :h5 ~@body))
(defmacro h6 [& body] `(element :h6 ~@body))
(defmacro header [& body] `(element :header ~@body))
(defmacro hgroup [& body] `(element :hgroup ~@body))
(defmacro hr [& body] `(element :hr ~@body))
(defmacro i [& body] `(element :i ~@body))
(defmacro iframe [& body] `(element :iframe ~@body))
(defmacro img [& body] `(element :img ~@body))
(defmacro input [& body] `(element :input ~@body))
(defmacro ins [& body] `(element :ins ~@body))
(defmacro kbd [& body] `(element :kbd ~@body))
(defmacro label [& body] `(element :label ~@body))
(defmacro legend [& body] `(element :legend ~@body))
(defmacro li [& body] `(element :li ~@body))
(defmacro link [& body] `(element :link ~@body))
(defmacro main [& body] `(element :main ~@body))
#_(defmacro map [& body] `(element :map ~@body))
(defmacro mark [& body] `(element :mark ~@body))
(defmacro math [& body] `(element :math ~@body))
(defmacro menu [& body] `(element :menu ~@body))
(defmacro itemprop [& body] `(element :itemprop ~@body))
(defmacro meter [& body] `(element :meter ~@body))
(defmacro nav [& body] `(element :nav ~@body))
(defmacro noscript [& body] `(element :noscript ~@body))
(defmacro object [& body] `(element :object ~@body))
(defmacro ol [& body] `(element :ol ~@body))
(defmacro option [& body] `(element :option ~@body))
(defmacro output [& body] `(element :output ~@body))
(defmacro p [& body] `(element :p ~@body))
(defmacro picture [& body] `(element :picture ~@body))
(defmacro pre [& body] `(element :pre ~@body))
(defmacro progress [& body] `(element :progress ~@body))
(defmacro q [& body] `(element :q ~@body))
(defmacro ruby [& body] `(element :ruby ~@body))
(defmacro s [& body] `(element :s ~@body))
(defmacro samp [& body] `(element :samp ~@body))
(defmacro script [& body] `(element :script ~@body))
(defmacro section [& body] `(element :section ~@body))
(defmacro select [& body] `(element :select ~@body))
(defmacro slot [& body] `(element :slot ~@body))
(defmacro small [& body] `(element :small ~@body))
(defmacro span [& body] `(element :span ~@body))
(defmacro strong [& body] `(element :strong ~@body))
(defmacro sub [& body] `(element :sub ~@body))
(defmacro sup [& body] `(element :sup ~@body))
(defmacro table [& body] `(element :table ~@body))
(defmacro tbody [& body] `(element :tbody ~@body))
(defmacro td [& body] `(element :td ~@body))
(defmacro th [& body] `(element :th ~@body))
(defmacro thead [& body] `(element :thead ~@body))
(defmacro tr [& body] `(element :tr ~@body))
(defmacro template [& body] `(element :template ~@body))
(defmacro textarea [& body] `(element :textarea ~@body))
(defmacro time [& body] `(element :time ~@body))
(defmacro u [& body] `(element :u ~@body))
(defmacro ul [& body] `(element :ul ~@body))
(defmacro var [& body] `(element :var ~@body))
(defmacro video [& body] `(element :video ~@body))
(defmacro wbr [& body] `(element :wbr ~@body))