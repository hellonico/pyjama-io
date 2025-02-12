(ns pyjama.io.embeddings
  (:require
    [clojure.string :as str]
    [mikera.vectorz.core :as vectorz]
    [pyjama.embeddings]
    [pyjama.io.readers]
    [taoensso.nippy :as nippy])
  (:import (java.io DataInputStream DataOutputStream FileInputStream FileOutputStream)
           (mikera.vectorz Vector)))

;; Register custom serialization for vectorz.Vector
(nippy/extend-freeze Vector ::vectorz-vector
                     [x data-output]
                     (nippy/freeze-to-out! data-output (.asDoubleArray x))) ;; Store as a double arra

(nippy/extend-thaw ::vectorz-vector
                   [data-input]
                   (vectorz/vec (nippy/thaw-from-in! data-input))) ;; Convert back to Vector
;(def nippy-options {:allow-unsafe-serialization? true})

(defn save-documents [filename docs]
  (with-open [out (DataOutputStream. (FileOutputStream. ^String filename))]
    (nippy/freeze-to-out! out docs)))

(defn load-documents [filename]
  (with-open [in (DataInputStream. (FileInputStream. ^String filename))]
    (nippy/thaw-from-in! in)))

;
;
;

(defn generate-vectorz-file [config file]
  (println "Embeddings:" file)
  (let [text (pyjama.io.readers/extract-text file)
        embeddings (pyjama.embeddings/generate-vectorz-documents (assoc config :documents text))]
    (map #(assoc % :file file) embeddings)))

(defn generate-vectorz-folder [config folder extensions]
  (let [files (filter #(.isFile %) (file-seq (clojure.java.io/file folder))) ;; Exclude directories
        valid-files (if (seq extensions)
                      (filter #(some (fn [ext] (str/ends-with? (str %) ext)) extensions) files)
                      files)]
    (mapcat #(generate-vectorz-file config %) valid-files)))
