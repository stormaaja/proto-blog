(defproject proto-site "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [compojure "1.6.1"]
                 [ring "1.6.0"]
                 [ring/ring-defaults "0.3.2"]
                 [hiccup "1.0.5"]
                 [clj-wiite "0.1.0"]]
  :plugins [[lein-ring "0.12.4"]]
  :ring {:handler proto-site.handler/app}
  :resource-paths ["resources"]
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.2"]]
         :resource-paths ["resources"]}})
