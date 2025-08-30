(ns pyjama.io.cache
  "Persistent, transparent memoization backed by SQLite.

  - Stores results per (function-name, input) primary key in pyjama.cache.db.
  - Inputs/outputs are serialized with pr-str and edn/read-string.
  - Intended for pure, deterministic single-argument functions.
  - The cache table is created at load time.

  Key functions:
  - memoize-to-sqlite: wrap a unary function so results are cached by fn-name.
  - get-cached, cache-result, list-cache-keys, init-db.

  Example:
  (alter-var-root #'pyjama.io.readers/extract-text
    (fn [orig] (pyjama.io.cache/memoize-to-sqlite \"extract-text\" orig)))"
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.edn :as edn]))

(def db {:classname   "org.sqlite.JDBC"
         :subprotocol "sqlite"
         :subname     "pyjama.cache.db"})

(defn init-db []
  (jdbc/execute! db ["CREATE TABLE IF NOT EXISTS cache (
                       function TEXT,
                       input TEXT,
                       output TEXT,
                       PRIMARY KEY (function, input))"]))

(defn get-cached [fn-name input]
  (let [result (jdbc/query db ["SELECT output FROM cache WHERE function = ? AND input = ?" fn-name input])]
    (when-let [row (first result)]
      (edn/read-string (:output row)))))

(defn cache-result [fn-name input output]
  (jdbc/execute! db ["INSERT OR REPLACE INTO cache (function, input, output) VALUES (?, ?, ?)"
                     fn-name input (pr-str output)]))

(defn list-cache-keys [fn-name]
  (map :input (jdbc/query db ["SELECT input FROM cache WHERE function = ?" fn-name])))

(defn memoize-to-sqlite [fn-name f]
  (fn [input]
    (or (get-cached fn-name input)
        (let [result (f input)]
          (cache-result fn-name input result)
          result))))

(init-db)

(comment

  (alter-var-root #'pyjama.io.readers/extract-text
                  (fn [original-fn]
                    (pyjama.io.cache/memoize-to-sqlite "extract-text" original-fn)))

  )