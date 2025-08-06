(ns pyjama.io.core
  (:require [clj-http.client :as client]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [pyjama.utils]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str])
  (:import (java.io File)))

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

(defn get-extension [filename]
  (let [parts (str/split filename #"\.")]
    (when (> (count parts) 1)                               ; Ensure there's an extension
      (str/lower-case (last parts)))))

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

(defn download-file [url]
 (let [{:keys [body headers]} (client/get url {:as :stream})
       ext     (get-extension url)
       temp    (File/createTempFile "llama-parse" ext)]
  (with-open [in-stream body
              out-stream (io/output-stream temp)]
   (io/copy in-stream out-stream))
  (.getAbsolutePath temp)))