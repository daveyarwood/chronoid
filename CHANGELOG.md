# CHANGELOG

## 0.1.1 (2016-02-20)

* Fix buggy behavior caused by self-referential JS objects. An event object contained a reference to its clock, and a clock contained a queue of events. Interestingly, Chrome, Firefox and Safari were all able to handle the stack overflow that this caused, so chronoid was able to work in browsers in spite of it. However, it is noticeable when running chronoid in a ClojureScript REPL, as whenever an event is added to a clock's event queue, the stacktrace is printed in the REPL.

  To remedy this, as of this release, events no longer contain a reference to their clocks. Instead, each time a clock is generated, a gensym id is created along with it, and stored in a `*clocks*` hash of clock ids to clocks. Events now contain a clock ID rather than a clock.

  This change is purely internal -- you can still use chronoid the same way (the way described in the README). When creating events, you can pass your reference to the clock itself and chronoid will translate that into the clock ID when creating the event internally.

## 0.1.0 (2015-08-02)

* Initial release.
