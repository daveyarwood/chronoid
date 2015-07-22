(ns chronoid.core)

(def default-options
  {:tolerance-late 0.10
   :tolerance-early 0.001})

(defrecord Clock [context])

(defn clock
  ([]        (Clock. (or js/window.AudioContext 
                         js/window.webkitAudioContext)))
  ([context] (Clock. context)))

