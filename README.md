# chronoid

[![Clojars Project](http://clojars.org/chronoid/latest-version.svg)](http://clojars.org/chronoid)

Sometimes you want to schedule events in the browser with a great degree of precision. This is especially relevant for web audio applications. Unfortunately, the JavaScript clock is not very precise. It is only precise down to the millisecond (which sounds precise, but for audio applications, doesn't quite cut it),
and, the truly bad part, the callback of timer events in JavaScript through `setTimeout` or `setInterval` can easily be skewed by ten milliseconds
or more by any number of things happening on the main execution thread, including layout, rendering, garbage collection and XHR.
As Chris Wilson explains in [A Tale of Two Clocks](http://www.html5rocks.com/en/tutorials/audio/scheduling), the Web Audio API exposes access to the audio subsystem's hardware clock, and provides functions for scheduling audio events according to this clock. It is a great deal more precise, but on its own, it doesn't quite allow us the flexibility we need in terms of scheduling events.
It turns out that combining the two clocks gives us the best of both worlds.

Chronoid is essentially a port of [WAAClock](https://github.com/sebpiq/WAAClock), a JavaScript library implementing the technique described in the aforementioned Chris Wilson article in order to help you schedule events in time using the Web Audio API clock. An event can be any arbitrary function, so this library can be useful in both audio and non-audio contexts.

## Usage

```clojure
(require '[chronoid.core :as c])

(def clock (c/clock))
(c/start! clock)
```

(These examples are loosely translated from the examples in the WAAClock README.)

### Schedule custom events

```clojure
; prints "wow!" at time marking 13000 (13 seconds from when the clock started)
(c/callback-at-time! clock #(js/console.log "wow!") 13000)

; prints "wow!" 13 seconds from now
(c/set-timeout! clock #(js/console.log "wow!") 13000)
```

### Set events to repeat periodically

```clojure
(-> (c/callback-at-time! clock #(js/console.log "wow!") 3000)
    (c/repeat! 2000))
```

### Cancel an event

For the following examples, we'll use [mantra](http://github.com/daveyarwood/mantra) to make some bleeps and bloops with a sine-wave oscillator.

```clojure
(require '[mantra.core :as m])

(def sine (m/osc :type :sine))

(defn bleep! []
  (m/play-note sine {:pitch 440 :duration 250}))

(defn bloop! []
  (m/play-note sine {:pitch 220 :duration 250}))

; gimme a bleep in 13 seconds
(def bleep (c/set-timeout! clock bleep! 13000))

; just kidding, cancel that
(c/clear! bleep)
```

### Change the tempo of one or more events

```clojure
(def bleep
  (-> (c/callback-at-time! clock bleep! 1000)
      (c/repeat! 2000)))

(def bloop
  (-> (c/callback-at-time! clock bloop! 2000)
      (c/repeat! 2000)))

; in 10 seconds, multiply the tempo by 2
(c/set-timeout! clock #(c/time-stretch! [bleep bloop] 0.5) 10000)
```

## License

Copyright © 2015-2016 Dave Yarwood

Distributed under the Eclipse Public License version 1.0.

