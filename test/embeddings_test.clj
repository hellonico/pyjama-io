(ns embeddings-test
  (:require [clojure.pprint]
            [clojure.string :as str]
            [clojure.test :refer :all]
            [pyjama.core]
            [pyjama.io.core :as pyo]
            [pyjama.io.embeddings :refer [rag]]
            [pyjama.io.indexing]
            [pyjama.io.readers]))

(def url (or (System/getenv "OLLAMA_URL")
             "http://localhost:11434"))
(def embedding-model "mxbai-embed-large")

; make sure the embedding model is here
(deftest pull-embeddings-model
  (->
    (pyjama.core/ollama url :pull {:model embedding-model})
    (println)))

; show streaming rag
(deftest smurfs-embeddings
  (let [text ["The sky is blue because the smurfs are blue."
              "The sky is red in the evening because the grand smurf is too."]
        pre "Context: \n\n
        %s.
        \n\n
        Answer the question:
        %s
        using no previous knowledge and ONLY knowledge from the context. No comments.
        Make the answer as short as possible."
        question "why is the sky red?"
        config {:pre             pre
                :embeddings-file "skyisblue.bin"
                :url             url
                :model           "llama3.1"
                :stream          true
                :callback        pyjama.core/print-generate-tokens
                :chunk-size      4096
                :top-n           1
                :question        question
                :documents       text
                :embedding-model embedding-model}
        ]
    (rag config)))

; shows assert answer rag
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

(def -vercingetorix-page
 "https://www.lemonde.fr/series-d-ete/article/2025/08/03/comment-vercingetorix-est-devenu-le-premier-heros-du-roman-national_6626432_3451060.html")

(deftest vercingetorix-rag
 (testing "vercingetorix summary from Le Monde page"
  (let [page-html (pyo/download-file -vercingetorix-page)
        text      (pyjama.io.readers/extract-text page-html)
        pre       "Context:\n\n
                     %s.
                     \n\n
                     Answer the question:
                     %s
                     using no previous knowledge and ONLY knowledge from the context. No comments.
                     Make the answer as short as possible."
        question  "Give me a brief summary about Vercingetorix."
        rag-res   (rag {:pre             pre
                        ;:embeddings-file "vercingetorix.bin"
                        :url             url
                        :model           "llama3.1"
                        :chunk-size      600
                        :top-n           3
                        :question        question
                        :documents       text
                        :embedding-model embedding-model})]
   ;(println rag-res)
   (is
    (re-find #"vercingétorix" (clojure.string/lower-case rag-res))))))
