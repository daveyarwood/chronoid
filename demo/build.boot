(set-env!
  :source-paths   #{"src"}
  :resource-paths #{"assets"}
  :dependencies   '[[org.clojure/clojure       "1.7.0"]
                    [org.clojure/clojurescript "1.7.228"]
                    [adzerk/boot-reload        "0.3.1"]
                    [pandeiro/boot-http        "0.6.2"]
                    [adzerk/boot-cljs          "1.7.228-1" :scope "test"]
                    [adzerk/boot-cljs-repl     "0.3.0"     :scope "test"]
                    [com.cemerick/piggieback   "0.2.1"     :scope "test"]
                    [weasel                    "0.7.0"     :scope "test"]
                    [org.clojure/tools.nrepl   "0.2.12"    :scope "test"]
                    [hoplon                    "6.0.0-alpha13"]
                    [hoplon/boot-hoplon        "0.1.13"]
                    [mantra                    "0.5.1"]
                    [chronoid                  "0.1.0"]])

(require
  '[adzerk.boot-cljs      :refer (cljs)]
  '[adzerk.boot-cljs-repl :refer (cljs-repl start-repl)]
  '[adzerk.boot-reload    :refer (reload)]
  '[pandeiro.boot-http    :refer (serve)]
  '[hoplon.boot-hoplon    :refer (hoplon prerender html2cljs)])

(deftask dev
  "Build for local development."
  []
  (merge-env! :source-paths #{"src"})
  (comp (watch)
        (speak)
        (hoplon :pretty-print true)
        (reload)
        (cljs-repl)
        (cljs :source-map true)
        (serve)))
