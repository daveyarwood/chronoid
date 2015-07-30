(ns chronoid.core)

; TODO:
; - resolve the scheduling/state conflicts between the `tick` function (which,
;   every ~5 ms, updates the clock events by removing any ones that are past
;   their deadline) and the `schedule!` function (which, when called by the
;   `execute!` function to schedule repeat events, inserts a new event into the
;   event queue). We don't have STM in ClojureScript, so I think what we need
;   here is something to coordinate the state of the event queue. Maybe a 
;   separate "new events queue" that the `tick` function can read from, and 
;   make sure that the new events actually get added.

(def ^:dynamic *audio-context*
  (let [ctx (or js/window.AudioContext 
                js/window.webkitAudioContext)]
    (ctx.)))

(def default-options
  {:context *audio-context*
   :tolerance-late  100 ; ms
   :tolerance-early 1}) ; ms

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

(defn- current-time*
  "Internal implementation for the public function current-time, which works
   on atom-wrapped clocks. 
   
   This internal version works on non-atom-wrapped clocks."
  [{:keys [context] :as clock}]
  (* 1000 (.-currentTime context)))

(defn current-time
  "Returns the current time of a clock's audio context, in milliseconds."
  [clock]
  (current-time* @clock))

(defn- absolute-time 
  "Converts from relative -> absolute time."
  [clock rel-time]
  (+ rel-time (current-time* clock)))

(defn- relative-time
  "Converts from absolute -> relative time."
  [clock abs-time]
  (- abs-time (current-time* clock)))

(defn- event*
  "Constructor for an event. Requires `action`, `clock` (as an atom) and
   `deadline` at the minimum. 
   
   Assigns a randomly generated id for the event if an :id is not provided to
   the constructor. This is useful for removing and repeating events.

   The tolerance interval (:latest-time and :earliest-time) is calculated 
   based on the deadline and :tolerance-early and :tolerance-late, which are
   either provided as keyword arguments, or taken from the clock's options."
  [{:keys [id clock deadline tolerance-early tolerance-late] :as event}]
  (let [id       (or id (gensym 'event))
        latest   (+ deadline (or tolerance-late  (:tolerance-late @clock)))
        earliest (- deadline (or tolerance-early (:tolerance-early @clock)))] 
    (assoc event :id id :latest-time latest :earliest-time earliest)))

(declare execute!)

(defn- tick
  "This function is ran periodically, and at each tick it executes
   events for which `currentTime` is included in their tolerance interval."
  [{:keys [events] :as clock}]
  (let [execute-now?    #(<= (:earliest-time %) (current-time* clock))
        ; there needs to be a tiny window of time after an event executes
        ; that it stays in the events queue... otherwise, it somehow gets taken
        ; out of the events queue before it can execute ¯\_(ツ)_/¯
        ready-to-clear? #(>= (- (current-time* clock) (:earliest-time %)) 5)]
    (doseq [event (take-while execute-now? events)]
      (execute! event))
    (doseq [event (filter ready-to-clear? events)]
      (js/console.log (str "clearing event, "
                           (:earliest-time event)
                           " < "
                           (current-time* clock))))
    (update clock :events #(drop-while ready-to-clear? %))))

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

(defn- create-event!
  "Create an event and insert into a clock's event queue."
  [clock f deadline & {:as opts}]
  (let [event (event* (merge {:action   f 
                              :clock    clock 
                              :deadline deadline}
                             opts))] 
    (swap! clock update :events insert-event event)
    event))

(defn- schedule!
  "Schedule a copy of an event with a new deadline."
  [{:keys [clock] :as event} new-deadline]
  (let [new-event (event* (assoc event :deadline new-deadline))]
    (swap! clock update :events insert-event new-event)))

(defn- execute!
  [{:keys [action clock latest-time deadline repeat-time] :as event}]
  (when (< (current-time clock) latest-time)
    (action))
  (when repeat-time
    (schedule! event (+ deadline repeat-time))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-timeout!
  "Schedules `f` after `delay` milliseconds. Returns the event.
   
   `opts` may contain :tolerance-early and :tolerance-late for optionally
   overriding the clock's timing window for events."
  [clock f delay & {:as opts}]
  (create-event! clock f (absolute-time @clock delay) opts)) 

(defn callback-at-time!
  "Schedules `f` to run before `deadline`. Returns the event.
   
   `opts` may contain :tolerance-early and :tolerance-late for optionally
   overriding the clock's timing window for events."
  [clock f deadline & {:as opts}]
  (create-event! clock f deadline opts))

(defn clear!
  "Unschedules an event by removing it from its clock's event queue."
  [{:keys [clock] :as event}]
  (swap! clock update :events filter (fn [{:keys [id]}] 
                                       (not= id (:id event))))
  event)

(defn repeat!
  "Sets the event to repeat every `time` milliseconds "
  [{:keys [clock deadline] :as event} time]
  {:pre [(pos? time)]}
  (schedule! (assoc event :repeat-time time) (+ deadline time)))

(defn- time-stretch!*
  "Internal implementation for time-stretching a single event.
   
   The public function below this one can handle a single event or multiple
   events."
  [{:keys [repeat-time deadline earliest-time clock] :as event} 
   time-reference 
   ratio]
  (clear! event)
  (let [deadline    (+ time-reference (* ratio (- deadline time-reference)))
        repeat-time (when repeat-time (* repeat-time ratio))
        repeats     (when repeat-time
                      (iterate (partial + repeat-time) deadline))]
    (schedule! (assoc event 
                :deadline (if repeats
                            (first (drop-while #(>= (current-time clock)
                                                    (- % earliest-time))
                                               repeats))
                            deadline)
                :repeat-time repeat-time)
               deadline)))

(defn time-stretch!
  "Reschedules events according to a `time-reference` and a `ratio`.

   The first argument can be either a single event or a list of events.
   
   e.g.
   (time-stretch! e (current-time clock) 0.5)
   
   ^-- makes an event `e` occur twice as soon as it would otherwise"
  [e time-reference ratio]
  (if (coll? e)
    (doseq [event e] (time-stretch!* event time-reference ratio))
    (time-stretch!* e time-reference ratio)))

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
