# CHANGELOG

## 0.2.0 (2019-09-20)

Wow, it's been 3-1/2 years since Chronoid was last touched! Thanks, [shark8me],
for submitting the PR that prompted me to spend a little time today updating it.

* [In April 2018, Chrome started making it so that you can't create an audio
  context unless the user has interacted with the page first][autoplay-policy].
  [shark8me] contributed a patch to Chronoid to make it so that we hold off on
  creating the audio context until you create a clock via the `clock` function.
  You should, of course, hold off on doing _that_ until the user has interacted
  with your page.

* I did a lot of miscellaneous refactoring and general code improvement.

* There were a bunch of places that were at least somewhat prone to data races
  because we were derefing the same atom multiple times when we don't expect the
  value to be different, or derefing an atom and then swapping it afterwards and
  assuming the value is still the same, etc. I've fixed those so that in
  general, things should be happening more atomically.

* I found and fixed a bug where periodic repeats (via the `repeat!` function)
  weren't happening because of a race condition deep in Chronoid's internals.
  Long story short, we were swapping every clock atom nearly constantly, on
  every tick! I suspect that performance is much better now, and the library
  should be more reliable.

[autoplay-policy]: https://developers.google.com/web/updates/2017/09/autoplay-policy-changes

## 0.1.1 (2016-02-20)

* Fix buggy behavior caused by self-referential JS objects. An event object
  contained a reference to its clock, and a clock contained a queue of events.
  Interestingly, Chrome, Firefox and Safari were all able to handle the stack
  overflow that this caused, so chronoid was able to work in browsers in spite
  of it. However, it is noticeable when running chronoid in a ClojureScript
  REPL, as whenever an event is added to a clock's event queue, the stacktrace
  is printed in the REPL.

  To remedy this, as of this release, events no longer contain a reference to
  their clocks. Instead, each time a clock is generated, a gensym id is created
  along with it, and stored in a `*clocks*` hash of clock ids to clocks. Events
  now contain a clock ID rather than a clock.

  This change is purely internal -- you can still use chronoid the same way (the
  way described in the README). When creating events, you can pass your
  reference to the clock itself and chronoid will translate that into the clock
  ID when creating the event internally.

## 0.1.0 (2015-08-02)

* Initial release.

[shark8me]: https://github.com/shark8me
