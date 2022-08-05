(defproject com.amperity/separator "0.1.0-SNAPSHOT"
  :description "An efficient and defensive delimiter-separated-value parser."
  :url "https://github.com/amperity/separator"
  :license {:name "MIT License"
            :url "https://mit-license.org/"}

  :dependencies
  [[org.clojure/clojure "1.11.1"]]

  :java-source-paths ["src"]

  :profiles
  {:repl
   {:source-paths ["dev"]
    :repl-options {:init-ns separator.repl}
    :jvm-opts ["-XX:-OmitStackTraceInFastThrow"]
    :dependencies [[org.clojure/tools.namespace "1.1.0"]]}})
