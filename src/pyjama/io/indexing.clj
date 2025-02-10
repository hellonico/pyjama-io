(ns pyjama.io.indexing
  "This uses lucene to index documents. Also allows to retrieve text later."
  (:require [clojure.java.io :as io]
            [pyjama.io.core]
            [clojure.string :as str]
            [clucy.core :as clucy]
            [pyjama.io.readers :as readers])
  (:import (java.util.regex Pattern)))

;; Create an in-memory search index
;(defonce search-index (atom (clucy/memory-index)))
(def index-path "clucy")
(defonce search-index (atom (clucy/disk-index index-path)))

(defn extract-text [file]
  (readers/extract-text file))

(defn index-document[pdf]
  (println "Indexing:" pdf)
  (let [text (extract-text pdf)]
    (clucy/add @search-index {:id (.getAbsolutePath (io/as-file pdf)) :content text})))

(defn index-documents [folder-path]
  (let [pdf-files (pyjama.io.core/load-files-from-folders folder-path #{"pdf" "md" "epub" "txt"})]
    (doseq [pdf pdf-files]
      (index-document pdf))))

(defn- escape-lucene-query [s]
  (str/replace s #"[+\-&|!(){}\\[\\]^\"~*?:\\/]" "\\\\$0"))

(defn- build-query [keywords]
  (str/join " OR " (map (fn [{:keys [keyword relevance]}]
                          (str (escape-lucene-query keyword) "^" relevance))
                        keywords)))

(defn score-document [doc keywords]
  (reduce (fn [score {:keys [keyword relevance]}]
            (if (re-find (re-pattern (Pattern/quote keyword)) (:content doc))
              (+ score relevance)
              score))
          0
          keywords))

(defn best-matching-document [keywords]
  (let [query (build-query keywords)
        results (clucy/search @search-index query 1)       ;; Search using keywords
        scored-results (map (fn [doc] {:doc (:id doc) :score (score-document doc keywords)}) results)
        best-match (apply max-key :score scored-results)]
    best-match))

;
; SENTENCES COMMON
;

;; Function to split the text into sentences (simple sentence splitting using period as delimiter)
(defn- split-into-sentences [text]
  (->> (str/split text #"[。！？\n]")  ;; Split on full stop, exclamation mark, question mark, and newline
       (remove str/blank?)
       (map str/trim)))

;
; SEARCH-1
;
;; Function to find relevant sentences containing any keyword
(defn- find-relevant-sentences [text keywords]
  (let [sentences (split-into-sentences text)]
    (filter
      (fn [sentence]
        (some #(re-find (re-pattern (Pattern/quote (:keyword %))) sentence) keywords))
      sentences)))

;; Function to search for documents containing a keyword
(defn- search-in-document [doc-id]
  (let [doc-result (clucy/search @search-index (str "id:" doc-id) 1)]  ;; Search for the document by its ID
    (if (empty? doc-result)
      nil
      (:content (first doc-result)))))  ;; Get the content of the document

(defn extract-relevant-sentences-in-doc [doc-id keywords]
  (let [doc-content (search-in-document doc-id)]
    (if doc-content
      (find-relevant-sentences doc-content keywords)
      (println "Document not found or no content available."))))


;
; variant 2
;

;; Function to find sentences containing any keyword with context
(defn- find-relevant-sentences [sentences keywords]
  (let [kw-patterns (map #(re-pattern (Pattern/quote (:keyword %))) keywords)]
    (keep-indexed
      (fn [idx sentence]
        (when (some #(re-find % sentence) kw-patterns)
          {:sentence sentence
           :context [(get sentences (dec idx) "")  ;; Previous sentence
                     sentence
                     (get sentences (inc idx) "")]  ;; Next sentence
           }))
      sentences)))

;; Function to search for a document by its ID
(defn- search-in-document [doc-id]
  (let [doc-result (clucy/search @search-index (str "id:" doc-id) 1)]
    (when (seq doc-result)
      (:content (first doc-result)))))

;; Main function to extract relevant sentences with context
(defn extract-relevant-sentences-in-doc [doc-id keywords]
  (if-let [doc-content (search-in-document doc-id)]
    (let [sentences (split-into-sentences doc-content)
          relevant (find-relevant-sentences sentences keywords)]
      (if (seq relevant)
        relevant
        (println "No relevant sentences found.")))
    (println "Document not found or no content available.")))

;
; variant 3
;

;; Function to split text into `n` parts
(defn- split-into-parts [text n]
  (let [length (count text)
        part-size (Math/ceil (/ length (double n)))]
    (map #(subs text (* % part-size) (min length (* (inc %) part-size)))
         (range n))))

;; Function to check if a part contains any keyword
(defn- part-has-keyword? [part keywords]
  (some #(re-find (re-pattern (Pattern/quote (:keyword %))) part) keywords))

;; Function to search for a document by its ID
(defn- search-in-document [doc-id]
  (let [doc-result (clucy/search @search-index (str "id:" doc-id) 1)]
    (when (seq doc-result)
      (:content (first doc-result)))))
;; Main function to extract relevant parts of a document and return as text

(defn extract-relevant-text-parts [doc-id keywords & [num-parts]]
  (let [num-parts (or num-parts 2)]  ;; Default to 2 parts if not specified
    (if-let [doc-content (search-in-document doc-id)]
      (let [parts (split-into-parts doc-content num-parts)
            relevant-parts (filter #(part-has-keyword? % keywords) parts)]
        (if (seq relevant-parts)
          (str/join "\n\n" relevant-parts)  ;; Join relevant parts with double newlines
          "No relevant text found."))
      "Document not found or no content available.")))


(defn augmented-text [best-pdf keywords strategy]
  (condp = strategy
    :sentences (str/join "\n" (map :sentence (extract-relevant-sentences-in-doc best-pdf keywords)))
    :parts (extract-relevant-text-parts best-pdf keywords)
    :full (slurp best-pdf)
    (throw (Exception. "Invalid strategy"))))