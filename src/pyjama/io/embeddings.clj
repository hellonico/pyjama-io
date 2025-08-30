(ns pyjama.io.embeddings
  "Utilities for generating, persisting, and loading document embeddings, plus a thin RAG wrapper.

  - Generates mikera.vectorz.Vector embeddings via pyjama.embeddings from raw text, files, or folders.
  - Registers Nippy serializers for vectorz.Vector to efficiently persist/restore embeddings.
  - Caches generated documents to a deterministic file (default: /tmp/embeddings-<hash>.bin) unless :embeddings-file is provided.
  - Provides helpers to save/load persisted documents and to run a simple RAG flow over a corpus.

  Primary entry points:
  - load-documents: Returns a sequence of document maps with embeddings, loading from cache if available or generating and persisting otherwise.
  - save-documents / load-persisted-documents: Explicit persistence round-trip using Nippy.
  - generate-vectorz-file / generate-vectorz-folder: Build embeddings from a file or a folder (optionally filtered by extensions).
  - rag: Loads documents and delegates to pyjama.embeddings/simple-rag.

  Config keys:
  - :documents        Path to a file or directory, a raw string of text, or a vector of strings.
  - :embeddings-file  Optional path to the persistence file (overrides the default /tmp location).
  - :chunk-size       Optional; forwarded to embedding generation.
  - :embedding-model  Optional; forwarded to embedding generation.
  - :url              Optional; forwarded where applicable."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [mikera.vectorz.core :as vectorz]
    [pyjama.embeddings]
    [pyjama.io.core :as pyo]
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

(defn load-persisted-documents [filename]
  (with-open [in (DataInputStream. (FileInputStream. ^String filename))]
    (nippy/thaw-from-in! in)))

(defn generate-vectorz-file [config file]
  (println "Embeddings:" file)
  (let [text (pyjama.io.readers/extract-text file)
        embeddings (pyjama.embeddings/generate-vectorz-documents (assoc config :documents text))]
    (map #(assoc % :file file) embeddings)))

(defn generate-vectorz-folder [config folder extensions]
 (let [valid-paths (pyo/load-files-from-folders folder #(some (partial str/ends-with? %) extensions))
       files (map io/file valid-paths)]
  (mapcat #(generate-vectorz-file config %) files)))


(defn path->embedding-path [path]
 (let [hash (-> path .hashCode str)]
  (str "/tmp/embeddings-" hash ".bin")))


(defn load-documents [config]
  (let [input (:documents config)
        persist-file (or (:embeddings-file config) (path->embedding-path input))
        documents
        (cond
          (pyjama.io.core/file-exists? (str persist-file))
          (pyjama.io.embeddings/load-persisted-documents persist-file)

          (.isDirectory (io/as-file (str input)))
          (let [documents
                (pyjama.io.embeddings/generate-vectorz-folder
                  (select-keys config [:documents :url :chunk-size :embedding-model]) (:documents config) nil)]
            (pyjama.io.embeddings/save-documents persist-file documents) documents)

          (or (vector? input) (string? input))
          (let [_documents (pyjama.embeddings/generate-vectorz-documents config)]
            (pyjama.io.embeddings/save-documents persist-file _documents) _documents)
          :else
          (throw (Exception. "No documents"))

          )]
    documents))

(defn rag [config]
  (let [documents (pyjama.io.embeddings/load-documents config)]
    (pyjama.embeddings/simple-rag config documents)))