(ns pyjama.io.cli
 (:require [clojure.string :as str]
           [clojure.java.io :as io]
           [clojure.tools.cli :refer [parse-opts]]
           [pyjama.io.readers :refer [extract-text]]
           [pyjama.io.core :as pyo]
           [pyjama.io.embeddings :refer [path->embedding-path rag]])
 (:gen-class))


(def url (or (System/getenv "OLLAMA_URL")
             "http://localhost:11434"))
(def embedding-model "mxbai-embed-large")


(def cli-options
 [["-p" "--path PATH" "Path (URL, file or folder)"
   :parse-fn str
   :validate [#(not (str/blank? %)) "Path cannot be empty"]]
  ["-q" "--question TEXT" "Question to ask"]
  ["-m" "--model NAME" "Model name" :default "llama3.1"]
  ["-h" "--help" "Show help"]])

(def default-question "Give me a brief summary")

(def prompt-template
 "Context:\n\n%s.\n\nAnswer the question:\n%s\nusing no previous knowledge and ONLY knowledge from the context. No comments.\n")

(defn -main [& args]
 (let [parsed-opts (clojure.tools.cli/parse-opts args cli-options)
       {:keys [options errors summary]} parsed-opts
       {:keys [path question model help]} options]
  (cond
   help
   (do (println "Usage: clj -M -m pyjama.io.cli -p <path> [options]")
       (println summary))

   errors
   (do (println "Errors:")
       (doseq [e errors] (println e))
       (System/exit 1))

   :else
   (let [resolved-path (if (str/starts-with? path "http") (pyo/download-file path) path)
         text (extract-text resolved-path)
         q (or question default-question)
         res (rag {:pre             prompt-template
                   :url             url
                   :model           model
                   ;:chunk-size      600
                   :top-n           3
                   :question        q
                   ;:stream          true
                   :documents       text
                   :embedding-model embedding-model})]
    (println "\n📘 Answer:\n" res)))))
