(ns chronoid.core)

(def default-options
  {:tolerance-late  100 ; ms
   :tolerance-early 1}) ; ms

(def ^:dynamic *clocks* {})

(def audio-context
  "An atomic reference to a global audio context that gets created the first
   time you call `clock` and is reused for any subsequent clocks."
  (atom nil))

(defn new-audio-context
  []
  (let [Context (or js/window.AudioContext js/window.webkitAudioContext)]
    (Context.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; For all of the public functions, the `clock` arguments are atom references to
;; maps representing clocks. The `clock` function returns such a clock atom,
;; providing a convenient abstraction so that callers need not worry about
;; atoms.

(defn clock
  [& {:as attrs}]
  (let [ctx        (swap! audio-context #(or % (new-audio-context)))
        id         (gensym "clock")
        clock      (merge default-options
                          attrs
                          {:context ctx
                           :id      id
                           :events  []
                           :started false})
        clock-atom (atom clock)]
    (set! *clocks* (assoc *clocks* id clock-atom))
    clock-atom))

(defn current-time
  "Returns the current time of a clock's audio context, in milliseconds."
  [{:keys [context]}]
  (* 1000 (.-currentTime context)))

(defn- event*
  "Constructor for an event. Requires `action`, `clock` (as an atom) and
   `deadline` at the minimum.

   Assigns a randomly generated id for the event if an :id is not provided to
   the constructor. This is useful for removing and repeating events.

   The tolerance interval (:latest-time and :earliest-time) is calculated
   based on the deadline and :tolerance-early and :tolerance-late, which are
   either provided as keyword arguments, or taken from the clock's options."
  [{:keys [id clock-id clock deadline tolerance-early tolerance-late] :as event}]
  (let [clock    @(if clock-id
                    (get *clocks* clock-id)
                    clock)
        id       (or id (gensym 'event))
        latest   (+ deadline (or tolerance-late  (:tolerance-late clock)))
        earliest (- deadline (or tolerance-early (:tolerance-early clock)))]
    (-> event
        (assoc :id id
               :clock-id (:id clock)
               :latest-time latest
               :earliest-time earliest)
        (dissoc :clock))))

(declare execute! schedule*)

(defn- tick!
  "This function is run constantly, and at each tick it executes events for
   which `currentTime` is included in their tolerance interval."
  [clock-atom]
  (let [{:keys [events] :as clock} @clock-atom
        current-time (current-time clock)
        execute-now? #(<= (:earliest-time %) current-time)
        events-due   (take-while execute-now? events)]
    (doseq [event events-due] (execute! event))
    (when (seq events-due)
      (swap! clock-atom update :events #(drop-while execute-now? %)))))

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
  [clock-atom f deadline & [{:as opts}]]
  (let [event (event* (merge {:action   f
                              :clock    clock-atom
                              :deadline deadline}
                             opts))]
    (swap! clock-atom update :events insert-event event)
    event))

(defn- schedule*
  "Insert a copy of an event into an event queue with a new deadline."
  [events event new-deadline]
  (let [new-event (event* (assoc event :deadline new-deadline))]
    (insert-event events new-event)))

(defn- schedule!
  "Schedule a copy of an event with a new deadline."
  [{:keys [clock-id] :as event} new-deadline]
  (let [clock-atom (get *clocks* clock-id)]
    (swap! clock-atom update :events schedule* event new-deadline)
    (event* (assoc event :deadline new-deadline))))

(declare repeat!)

(defn- execute!
  [{:keys [action clock-id latest-time repeat-time] :as event}]
  (let [clock @(get *clocks* clock-id)]
    (when (< (current-time clock) latest-time)
      (action))
    (when repeat-time
      (repeat! event repeat-time))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn set-timeout!
  "Schedules `f` after `delay-ms` milliseconds. Returns the event.

   `opts` may contain :tolerance-early and :tolerance-late for optionally
   overriding the clock's timing window for events."
  [clock-atom f delay-ms & {:as opts}]
  (create-event! clock-atom f (+ (current-time @clock-atom) delay-ms) opts))

(defn callback-at-time!
  "Schedules `f` to run before `deadline`. Returns the event.

   `opts` may contain :tolerance-early and :tolerance-late for optionally
   overriding the clock's timing window for events."
  [clock-atom f deadline & {:as opts}]
  (create-event! clock-atom f deadline opts))

(defn clear!
  "Unschedules an event by removing it from its clock's event queue."
  [{:keys [clock-id] :as event}]
  (let [clock-atom (get *clocks* clock-id)]
    (swap! clock-atom update :events #(filter (fn [{:keys [id]}]
                                                (not= id (:id event)))
                                              %))
    event))

(defn repeat!
  "Sets the event to repeat every `time` milliseconds "
  [{:keys [deadline] :as event} time]
  {:pre [(pos? time)]}
  (-> event
      (assoc :repeat-time time)
      (schedule! (+ deadline time))))

(defn- time-stretch!*
  "Internal implementation for time-stretching a single event."
  [{:keys [repeat-time clock-id deadline] :as event}
   time-reference
   ratio]
  (let [clock       @(get *clocks* clock-id)
        deadline    (+ time-reference (* ratio (- deadline time-reference)))
        repeat-time (when repeat-time (* repeat-time ratio))
        repeats     (when repeat-time
                      (iterate (partial + repeat-time) deadline))]
    (clear! event)
    (schedule! (assoc event :repeat-time repeat-time)
               (if repeats
                 (first (drop-while #(>= (current-time clock) %) repeats))
                 deadline))))

(defn time-stretch!
  "Reschedules events according to a `time-reference` and a `ratio`.
   If the event is a repeating event, adjusts its repeat-time accordingly.

   The first argument can be either a single event or a list of events.
   Returns the rescheduled event, or a list of rescheduled events, depending
   on the input type.

   e.g.
   (time-stretch! e (current-time clock) 0.5)
   ^-- makes an event `e` occur twice as soon as it would otherwise

   If `time-reference` is omitted, the default value is the current time of the
   event's clock."
  ([e ratio]
   (let [{:keys [clock-id]} (if (sequential? e) (first e) e)
         clock @(get *clocks* clock-id)]
     (time-stretch! e (current-time clock) ratio)))
  ([e time-reference ratio]
   (if (sequential? e)
     (doall (map #(time-stretch!* % time-reference ratio) e))
     (time-stretch!* e time-reference ratio))))

(defn start!
  "Remove all scheduled events and start the clock."
  [clock-atom]
  (swap! clock-atom
         (fn [{:keys [context started] :as clock}]
           (if started
             clock
             (let [clock-node (doto (.createScriptProcessor context 256 1 1)
                                (.connect (.-destination context))
                                (aset "onaudioprocess" #(tick! clock-atom)))]
               (assoc clock
                      :started    true
                      :events     []
                      :clock-node clock-node))))))

(defn stop!
  "Stops the clock."
  [clock-atom]
  (swap! clock-atom
         (fn [{:keys [started clock-node] :as clock}]
           (if started
             (do
               (.disconnect clock-node)
               (-> clock (assoc :started false) (dissoc :clock-node)))
             clock))))
