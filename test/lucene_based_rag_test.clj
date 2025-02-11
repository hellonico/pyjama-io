(ns lucene-based-rag-test
  (:require [clojure.test :refer :all]
            [pyjama.functions :refer [ollama-fn]]
            [pyjama.io.core :as pyo]
            [pyjama.io.indexing]
            ))

(def -keyworder-settings
  {
   :url   "http://localhost:11432"
   :model "llama3.1"
   :format
   {
    :type     "array"
    :minItems 4
    :maxItems 8
    :items    {:type       "object"
               :required   [:keyword :relevance]
               :properties {:keyword {:type "string" :minLength 2} :relevance {:type "integer"}}}}
   :system
   "Find all the main keywords in the each prompt. relevance is how important it is in the sentence betweem 1 and 10"
   })


(def -answerer-settings
  {
   :url    "http://localhost:11432"
   :model  "llama3.1"
   :stream true
   :pre
   "With this knowledge:
   ====================
   %s
   ====================
   Answer the question:\n %s \n in maximum 3 to 5 words."
   })

(def -document "https://www.toyota.com/content/dam/toyota/brochures/pdf/2025/gr86_ebrochure.pdf")

(defn lucene-based-rag [pdf-file search-stategy keyworder-settings answerer-settings]
  (let [_ (pyjama.io.indexing/index-document pdf-file)
        keyworder (ollama-fn keyworder-settings)
        question "What is the new Toyota model?"
        keywords (keyworder question)
        answerer (ollama-fn answerer-settings)
        ]
    (-> question
        (keyworder)
        (pyjama.io.indexing/best-matching-document)
        :doc
        (pyjama.io.indexing/augmented-text keywords :sentences)
        (#(answerer [% question])))))

(deftest indexing
  (lucene-based-rag (pyo/download-file -document)
                    :sentences -keyworder-settings -answerer-settings))