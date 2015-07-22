(set-env! 
  :source-paths #{"src"}
  :dependencies '[[org.clojure/clojurescript "0.0-3308"]
                  [adzerk/bootlaces "0.1.11" :scope "test"]])

(require '[adzerk.bootlaces :refer :all])

(def +version+ "0.1.0")
(bootlaces! +version+)

(task-options!
  pom {:project 'chronoid
       :version +version+
       :description "A ClojureScript library for rock-solid scheduling of events."
       :url "https://github.com/daveyarwood/chronoid"
       :scm {:url "https://github.com/daveyarwood/chronoid"}
       :license {"name" "Eclipse Public License"
                 "url"  "http://www.eclipse.org/legal/epl-v10.html"}})

