(ns chronoid.core
  (:refer-clojure :exclude [repeat]))

(def ^:dynamic *audio-context*
  (let [ctx (or js/window.AudioContext 
                          js/window.webkitAudioContext)]
              (ctx.)))

(def default-options
  {:context *audio-context*
   :tolerance-late 0.10
   :tolerance-early 0.001})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

; For all of the public functions, the `clock` arguments are atom references to
; maps representing clocks. The `clock` function returns such a clock atom, 
; providing a convenient abstraction so that callers need not worry about atoms.

(defn clock
  [& {:as attrs}]
  (atom (merge default-options
               attrs
               {:events  []
                :started false})))

(defn- absolute-time 
  "Converts from relative -> absolute time."
  [{:keys [context] :as clock} rel-time]
  (+ rel-time (.-currentTime context)))

(defn- relative-time
  "Converts from absolute -> relative time."
  [{:keys [context] :as clock} abs-time]
  (- abs-time (.-currentTime context)))

(defn- event*
  "Constructor for an event. Requires `action`, `clock` (as an atom) and
   `deadline` at the minimum. 
   
   
   The tolerance interval (:latest-time and :earliest-time) are calculated 
   based on the deadline and :tolerance-early and :tolerance-late, which are
   either provided as keyword arguments, or taken from the clock's options."
  [& {:keys [clock deadline tolerance-early tolerance-late] :as event}]
  (let [latest   (+ deadline (or tolerance-late  (:tolerance-late @clock)))
        earliest (- deadline (or tolerance-early (:tolerance-early @clock)))] 
    (assoc event :latest-time latest :earliest-time earliest)))

(declare execute)

(defn- tick
  "This function is ran periodically, and at each tick it executes
   events for which `currentTime` is included in their tolerance interval."
  [{:keys [context events] :as clock}]
  (let [execute-now? #(<= (:earliest-time %) (.-currentTime context))] 
    (doseq [event (take-while execute-now? events)]
      (execute event))
    (update clock :events drop-while execute-now?)))

(defn- index-by-time 
  "Does a binary search to find the index of the first event whose deadline is
   >= `deadline`."
  [events deadline]
  (loop [low 0
         high (count events)
         mid (js/Math.floor (/ (+ low high) 2))]
    (if (< low high)
      (let [{:keys [earliest-time]} (nth events mid)
            action (if (< earliest-time deadline) :higher :lower)]
        (recur (if (= action :higher) (inc mid) low)
               (if (= action :lower) mid high)
               (js/Math.floor (/ (+ low high) 2))))
      low)))

(defn- insert-event
  "Insert an event into an event queue, properly sorted by deadline."
  [events {:keys [earliest-time] :as event}]
  (let [i (index-by-time events earliest-time)]
    (concat (take i events) [event] (drop i events))))

(defn- create-event
  "Create an event and insert into a clock's event queue.
   
   `opts` may contain :tolerance-early and :tolerance-late for optionally
   overriding the clock's timing window for events."
  [clock f deadline & {:as opts}]
  (let [event (event* :action   f
                      :clock    clock
                      :deadline deadline)] 
    (swap! clock update :events insert-event event)
    event))

(defn- schedule
  "Schedule a copy of an event with a new deadline."
  [{:keys [clock] :as event} new-deadline]
  (let [new-event (event* (assoc event :deadline new-deadline))]
    (swap! clock update :events insert-event new-event)))

(defn- execute
  [{:keys [action clock latest-time deadline repeat-time] :as event}]
  (let [{:keys [context]} @clock]
    (when (< (.-currentTime context) latest-time)
      (action))
    (schedule event (+ deadline repeat-time))))

; TODO: all the Event class functions; time-stretch

(defn clear
  "Unschedules an event by removing it from its clock's event queue."
  [{:keys [clock] :as event}]
  (swap! clock update :events filter #(not= % event))
  event)

(defn repeat
  "Sets the event to repeat every `time` milliseconds. 
   
   `time` must be > 0"
  [{:keys [clock deadline] :as event} time]
  {:pre [(pos? time)]}
  (schedule (assoc event :repeat-time time) (+ deadline time)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-timeout
  "Schedules `f` after `delay` milliseconds. Returns the event."
  [clock f delay]
  (create-event clock f (absolute-time clock delay))) 

(defn callback-at-time
  "Schedules `f` to run before `deadline`. Returns the event."
  [clock f deadline]
  (create-event clock f deadline))

(defn time-stretch
  [?]
  "TODO")

(defn start!
  "Remove all scheduled events and start the clock."
  [clock]
  (let [{:keys [context started]} @clock]
    (when-not started
      (swap! clock assoc :events [])
      (let [clock-node (doto (.createScriptProcessor context 256 1 1)
                         (.connect (.-destination context))
                         (aset "onaudioprocess" #(swap! clock tick)))]
      (swap! clock assoc :clock-node clock-node :started true)))))

(defn stop!
  "Stops the clock."
  [clock]
  (let [{:keys [started clock-node]} @clock]
    (when started
      (.disconnect clock-node)
      (swap! clock assoc :started false :clock-node nil))))
