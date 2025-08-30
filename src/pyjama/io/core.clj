(ns pyjama.io.core
  "General IO and utility helpers used across pyjama.io.

  Files:
  - file-exists?, file-empty?, get-extension (from URL path)
  - load-files-from-folders with an extension predicate

  Persistence:
  - save-to-file (pprint), pprint-to-file, read-settings (EDN)
  - save-to-csv, append-to-csv, load-best-documents

  Networking:
  - download-file streams a URL to disk (keeps/guesses extension)
  - resolve-path downloads if the input is an http(s) URL

  Aliases:
  - load-lines-of-file is delegated to pyjama.utils."
  (:require [clj-http.client :as client]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [pyjama.utils]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str])
  (:import (java.io File)
           (java.net URI)))

(defn file-empty? [file-path]
 (zero? (.length (io/file file-path))))

(defn file-exists? [filename]
 (.exists (io/as-file filename)))

(defn save-to-file [file-path best-docs]
 (spit file-path (with-out-str (pprint best-docs)))
 best-docs)

(defn save-to-csv
 ([output-file object]
  (save-to-csv output-file object nil))
 ([output-file object headers]
  (with-open [writer (io/writer output-file)]
   (csv/write-csv writer (cons headers object)))))

(defn append-to-csv
 ([output-file object]
  (with-open [writer (io/writer output-file :append true)]
   (csv/write-csv writer object))))

(defn load-best-documents [file-path]
 (read-string (slurp file-path)))

(def load-lines-of-file
 pyjama.utils/load-lines-of-file)

(defn get-extension [url]
 (let [path (.getPath (URI. url))
       filename (last (str/split path #"/"))]
  (second (re-matches #".*\.([a-zA-Z0-9]+)$" filename))))

(defn load-files-from-folders [folder-path valid-extensions]
 (->> (io/file folder-path)
      .listFiles
      (filter #(and (.isFile %) (valid-extensions (get-extension (.getName %)))))
      (map #(.getPath %))))

(defn pprint-to-file [file data]
 (with-open [w (clojure.java.io/writer file)]
  (binding [*out* w]
   (pprint data))))

(defn read-settings [path]
 (cond
  (nil? path) false
  (not (.exists (clojure.java.io/as-file path))) false
  :default (read-string (slurp path))))

(defn download-file
 ([url]
  (let [ext (get-extension url)
        ;; Ensure extension has leading dot
        ;suffix (if (str/starts-with? ext ".") ext (str "." ext))
        suffix    (if ext (str "." ext) ".html")
        temp-file (File/createTempFile "llama-parse" suffix)]
   (download-file url (.getAbsolutePath temp-file))))

 ([url target-path]
  (let [{:keys [body]} (client/get url {:as :stream})
        file (File. target-path)]
   (with-open [in-stream body
               out-stream (io/output-stream file)]
    (io/copy in-stream out-stream))
   (.getAbsolutePath file))))


(defn resolve-path [path]
 (if (str/starts-with? path "http")
  (download-file path)
  path))


