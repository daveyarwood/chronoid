# chronoid

Sometimes you want to schedule events in the browser with a great degree of precision. This is especially relevant for web audio applications. Unfortunately, the JavaScript clock is not very precise. It is only precise down to the millisecond (which sounds precise, but for audio applications, doesn't quite cut it), 
and, the truly bad part, the callback of timer events in JavaScript through `setTimeout` or `setInterval` can easily be skewed by ten milliseconds
or more by any number of things happening on the main execution thread, including layout, rendering, garbage collection and XHR.
As Chris Wilson explains in [A Tale of Two Clocks](http://www.html5rocks.com/en/tutorials/audio/scheduling), the Web Audio API exposes access to the audio subsystem's hardware clock, and provides functions for scheduling audio events according to this clock. It is a great deal more precise, but on its own, it doesn't quite allow us the flexibility we need in terms of scheduling events.
It turns out that combining the two clocks gives us the best of both worlds.

Chronoid is essentially a port of [WAAClock](https://github.com/sebpiq/WAAClock), a JavaScript library implementing the technique described in the aforementioned Chris Wilson article in order to help you schedule events in time using the Web Audio API clock. An event can be any arbitrary function, so this library can be useful in both audio and non-audio contexts.

## Usage

*TODO*

## License

Copyright © 2015 Dave Yarwood

Distributed under the Eclipse Public License version 1.0.

