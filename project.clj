(defproject employeerepublic/slf4j-timbre "0.5.1"
  :description "SLF4J binding for Timbre"
  :url "https://github.com/employeerepublic/slf4j-timbre"
  :scm {:name "git"
        :url "https://github.com/employeerepublic/slf4j-timbre"}
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :profiles {:provided
             {:dependencies
              [[org.clojure/clojure "1.9.0"]
               [org.clojure/tools.reader "1.3.2"]
               [com.taoensso/timbre "4.10.0"
                :exclusions [org.clojure/tools.reader]]
               [org.slf4j/slf4j-api "1.7.25"]]}

             :dev
             {:dependencies [[midje "1.9.4"]]
              :plugins [[lein-midje "3.2.1"]]}}
  :aot :all)
