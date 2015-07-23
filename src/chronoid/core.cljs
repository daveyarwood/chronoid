(ns chronoid.core)

(def default-options
  {:context (or js/window.AudioContext 
                js/window.webkitAudioContext)
   :tolerance-late 0.10
   :tolerance-early 0.001})

(defrecord Clock [context]
  Object
  (foo [_] :bar))

(defn clock
  [& {:keys [context] :as opts}]
  (let [{:keys [context]} (merge default-options opts)]
    (Clock. context)))

