(defproject clojure-erlastic "0.3.1"
  :description "Micro lib making use of erlang JInterface lib to decode and
                encode Binary Erlang Term and simple erlang port interface with
                core.async channel. So you can communicate with erlang coroutine
                with clojure abstraction"
  :url "https://github.com/awetzel/clojure-erlastic"
  :scm {:name "git" :url "https://github.com/awetzel/clojure-erlastic"}
  :license {:name "The MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :global-vars {*warn-on-reflection* true}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/core.async "0.3.443"]
                 [org.erlang.otp/jinterface "1.6.1"]]
  :repositories {"scalaris" "https://scalaris-team.github.io/scalaris/maven"})
