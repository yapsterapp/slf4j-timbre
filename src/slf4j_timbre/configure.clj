(ns slf4j-timbre.configure
  "Timbre configuration.

  Put `logging_filters.edn` file in your resources. The file should
  contain a map of namespace prefixes (strings) to log levels."
  (:require [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn filter-middleware
  ([levels data]
   (let [ns-str (:?ns-str data "")
         results (filter (fn [[k level]]
                           (and (.startsWith ns-str k)
                                (not (timbre/level>= (:level data) level))))
                         levels)]
     (if (first results)
       nil
       data))))

(defn add-thread-name-middleware
  "Addds :thread-name entry"
  [data]
  (assoc data :thread-name (.getName (Thread/currentThread))))

(defn output-context
  [context]
  (when (not-empty context)
    (str
     " ["
     (->> context
          (map (fn [[k v]]
                 (str (name k) ":" v)))
          (interpose " ")
          (apply str))
     "]")))

(defn color-output-fn
  "Default (fn [data]) -> string output fn.
  Use`(partial color-output-fn <opts-map>)` to modify default opts."
  ([     data] (color-output-fn nil data))
  ([opts data] ; For partials
   (let [{:keys [no-stacktrace? stacktrace-fonts]} opts
         {:keys [level ?err #_vargs msg_ ?ns-str hostname_
                 timestamp_ ?line context]} data
         color {:info :green :warn :yellow :error :red}]
     (str
      (force timestamp_) " "
      (timbre/color-str (get color level :white)  (format "%-5s"(str/upper-case (name level)))) " "
      (timbre/color-str :cyan "[" (or ?ns-str "?") ":" (or ?line "?") "]") " "
      (if-let [tn (:thread-name data)]
        (format "[%s]" tn)
        "[?]")
      (output-context context)
      " - "
      (force msg_)
      (when-not no-stacktrace?
        (when-let [err ?err]
          (str "\n" (timbre/stacktrace err opts))))))))

(defn plain-output-fn
  "Default (fn [data]) -> string output fn.
  Use`(partial default-output-fn <opts-map>)` to modify default opts."
  ([     data] (color-output-fn nil data))
  ([opts data] ; For partials
   (let [{:keys [no-stacktrace? stacktrace-fonts]} opts
         {:keys [level ?err #_vargs msg_ ?ns-str hostname_
                 timestamp_ ?line context]} data
         color {:info :green :warn :yellow :error :red}]
     (str
      (force timestamp_) " "
      (format "%-5s"(str/upper-case (name level))) " "
      "[" (or ?ns-str "?") ":" (or ?line "?") "] "
      (if-let [tn (:thread-name data)]
        (format "[%s]" tn)
        "[?]")
      (output-context context)
      " - "
      (force msg_)
      (when-not no-stacktrace?
        (when-let [err ?err]
          (str "\n" (timbre/stacktrace err opts))))))))

(defn assoc-output-fn
  "put one of the predefinedd output-fns into the :println appender"
  [timbre-config opts]
  (assoc-in timbre-config
            [:appenders :println :output-fn]
            (if (:no-colors-in-logs? opts)
              plain-output-fn
              color-output-fn)))

(defn read-logging-filters
  "minimum levels for different namespaces"
  ([] (read-logging-filters "logging_filters.edn"))
  ([f]
   (if-let [r (io/resource
               f
               ;; there's a bug in io/resource which NPEs
               ;; when the context ClassLoader is null
               (or
                (.getContextClassLoader (Thread/currentThread))
                (ClassLoader/getSystemClassLoader)))]
     (do
       (timbre/info "found log filters in: " r)
       (some-> r slurp edn/read-string))
     (do
       (timbre/warn "no logging_filters.edn resource found")
       {}))))

(defn prepare-logging-config
  [opts]
  (let [logging-filters (->
                         (or (:logging-filters opts)
                             (some-> opts
                                     :logging-filters-file
                                     read-logging-filters))
                         (dissoc :root))
        filters (partial filter-middleware logging-filters)
        base-config timbre/example-config
        base-config (if-not (:short-timestamp-in-logs? opts)
                      base-config
                      (assoc-in base-config
                                [:timestamp-opts :pattern]
                                "HH:mm:ss"))]
    (-> base-config
        (update :middleware into [filters add-thread-name-middleware])
        (assoc-output-fn opts))))

(defn configure-timbre*

  ([]
   (let [lfs (read-logging-filters)]
     (configure-timbre*
      {:logging-filters lfs})))

  ([{:keys [opts
            level
            environment
            logging-filters]}]
   (try
     (let [l (or level
                 (:root logging-filters)
                 (condp = (some-> environment name)
                   "test" :warn
                   "production" :info
                   :debug))]
       (timbre/info "applying timbre config: " l)
       (timbre/set-level! l)
       (timbre/set-config!
        (prepare-logging-config opts))
       ;; set-config! overwrites the level
       (timbre/set-level! l))
     (catch Exception ex
       (timbre/error "Failed to configure logging: " (.getMessage ex))
       (throw ex)))))

(def ^:private configured (atom false))

(defn configure-timbre
  [& args]
  (swap!
   configured
   (fn [done?]
     (apply configure-timbre* args)
     true)))

(defn configure-timbre-once
  [& args]
  (swap!
   configured
   (fn [done?]
     (when-not done?
       (apply configure-timbre* args))
     true)))
