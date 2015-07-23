(ns chronoid.core)

(def default-options
  {:context (let [ctx (or js/window.AudioContext 
                          js/window.webkitAudioContext)]
              (ctx.))
   :tolerance-late 0.10
   :tolerance-early 0.001})

(defrecord Clock [context events]
  Object
  (foo [_] :bar))

(defn clock
  [& {:as opts}]
  (let [{:keys [context]} (merge default-options opts)]
    (Clock. context [])))

