(ns chronoid.core)

(def default-options
  {:context (let [ctx (or js/window.AudioContext 
                          js/window.webkitAudioContext)]
              (ctx.))
   :tolerance-late 0.10
   :tolerance-early 0.001})

(defrecord Clock [context events]
  Object
  (absolute-time 
    "Converts from relative -> absolute time."
    [_ rel-time]
    (+ rel-time (.-currentTime context)))
  (relative-time
    "Converts from absolute -> relative time."
    [_ abs-time
     (- abs-time (.-currentTime context))])
  (tick
    "This function is ran periodically, and at each tick it executes
     events for which `currentTime` is included in their tolerance interval."
    [_]
    "TODO")
  (set-timeout
    "Schedules `f` after `delay` milliseconds."
    [me f delay]
    (Event. (absolute-time me delay) f)))

(defn clock
  [& {:as opts}]
  (let [{:keys [context]} (merge default-options opts)]
    (Clock. context (atom []))))

