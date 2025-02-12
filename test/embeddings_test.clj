(ns embeddings-test
  (:require [clojure.pprint]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [pyjama.core]
            [pyjama.embeddings :refer [enhanced-context generate-vectorz-documents]]
            [pyjama.io.core :as pyo]
            [pyjama.io.embeddings]
            [pyjama.io.indexing]
            [pyjama.io.readers])
  (:import (java.io File)))

(def url (or (System/getenv "OLLAMA_URL")
             "http://localhost:11432"))
(def embedding-model
  "mxbai-embed-large")

(deftest pull-embeddings-model
  (->
    (pyjama.core/ollama url :pull {:model embedding-model})
    (println)))

(defn rag [config]
  (let [persist-file (or (:embeddings-file config) "embeddings.bin")
        documents
        (if (pyjama.io.core/file-exists? persist-file)
          (pyjama.io.embeddings/load-documents persist-file)
          (let [documents
                (generate-vectorz-documents
                  (select-keys config [:documents :url :chunk-size :embedding-model]))]
            (pyjama.io.embeddings/save-documents persist-file documents)
            documents))

        enhanced-context
        (enhanced-context
          (assoc
            (select-keys config
                         [:question :url :embedding-model :top-n])
            :documents documents
            ))]
    (pyjama.core/ollama
      url
      :generate
      (assoc
        (select-keys config [:options :stream :model :pre])
        :prompt [enhanced-context (:question config)])
      :response)))

(deftest smurfs-embeddings
  (let [text "The sky is blue because the smurfs are blue.
              The sky is red in the evening because the grand smurf is too."
        pre "Context: \n\n
        %s.
        \n\n
        Answer the question:
        %s
        using no previous knowledge and ONLY knowledge from the context. No comments.
        Make the answer as short as possible."
        question "why is the sky red?"
        ]
    (rag {:pre             pre
          :embeddings-file "skyisblue.bin"
          :url             url
          :model           "mistral"
          :stream          true
          :chunk-size      4096
          :top-n           1
          :question        question
          :documents       text
          :embedding-model embedding-model})))

(def -toyota-document "https://www.toyota.com/content/dam/toyota/brochures/pdf/2025/gr86_ebrochure.pdf")
(defn toyota-rag [question]
  (let [toyota (pyo/download-file -toyota-document)
        text (pyjama.io.readers/extract-text toyota)
        pre "Context: \n\n
        %s.
        \n\n
        Answer the question:
        %s
        using no previous knowledge and ONLY knowledge from the context. No comments.
        Make the answer as short as possible."
        ]
    (rag {:pre             pre
          :embeddings-file "toyota.bin"
          :url             url
          :model           "llama3.1"
          :chunk-size      600
          :top-n           3
          :question        question
          :documents       text
          :embedding-model embedding-model})))

(deftest toyota-embeddings
  (assert
    (str/includes? (toyota-rag "what's the latest toyota model?") "GR86"))
  (assert
    (str/includes? (toyota-rag "what's the name of the sonar of the toyota model?") "Rear Parking Sonar")))
